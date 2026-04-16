package com.picojava.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.picojava.common.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResponseParser {
    private static final Pattern TOOL_JSON_PATTERN = Pattern.compile("<tool>(.*?)</tool>", Pattern.DOTALL);
    private static final Pattern TOOL_XML_PATTERN = Pattern.compile("<tool(?<attrs>[^>]*)>(?<body>.*?)</tool>", Pattern.DOTALL);
    private static final Pattern FINAL_PATTERN = Pattern.compile("<final>(.*?)</final>", Pattern.DOTALL);
    private static final Pattern ATTR_PATTERN = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    private ResponseParser() {}

    public static ModelTurn parse(String text) {
        String raw = text == null ? "" : text;
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            return ModelTurn.retry(raw, retryNotice("模型返回了空响应"), "empty_response");
        }

        int toolIndex = raw.indexOf("<tool");
        int finalIndex = raw.indexOf("<final>");
        boolean toolFirst = toolIndex >= 0 && (finalIndex < 0 || toolIndex < finalIndex);

        if (toolFirst) {
            ModelTurn toolTurn = parseTool(raw);
            if (toolTurn != null) {
                return toolTurn;
            }
            return ModelTurn.retry(raw, retryNotice("模型返回的工具调用格式不正确"), "malformed_tool_output");
        }

        Matcher finalMatcher = FINAL_PATTERN.matcher(raw);
        if (finalMatcher.find()) {
            String content = finalMatcher.group(1).trim();
            if (content.isBlank()) {
                return ModelTurn.retry(raw, retryNotice("模型返回了空的 <final> 答案"), "empty_final");
            }
            return ModelTurn.finalAnswer(raw, new FinalAnswer(content));
        }

        return ModelTurn.finalAnswer(raw, new FinalAnswer(trimmed));
    }

    private static ModelTurn parseTool(String raw) {
        Matcher jsonMatcher = TOOL_JSON_PATTERN.matcher(raw);
        if (jsonMatcher.find()) {
            return parseJsonTool(raw, jsonMatcher.group(1).trim());
        }
        Matcher xmlMatcher = TOOL_XML_PATTERN.matcher(raw);
        if (xmlMatcher.find()) {
            return parseXmlTool(raw, xmlMatcher.group("attrs"), xmlMatcher.group("body"));
        }
        return null;
    }

    private static ModelTurn parseJsonTool(String raw, String body) {
        try {
            JsonNode node = JsonUtils.MAPPER.readTree(body);
            if (!node.isObject()) {
                return ModelTurn.retry(raw, retryNotice("工具 payload 必须是 JSON 对象"), "tool_payload_not_object");
            }
            String name = node.path("name").asText("").trim();
            if (name.isBlank()) {
                return ModelTurn.retry(raw, retryNotice("工具 payload 缺少工具名"), "missing_tool_name");
            }
            JsonNode argsNode = node.path("args");
            Map<String, Object> args;
            if (argsNode.isMissingNode() || argsNode.isNull()) {
                args = Map.of();
            } else if (!argsNode.isObject()) {
                return ModelTurn.retry(raw, retryNotice("工具 args 必须是 JSON 对象"), "tool_args_not_object");
            } else {
                args = JsonUtils.MAPPER.convertValue(argsNode, new TypeReference<>() {});
            }
            return ModelTurn.toolCall(raw, new ToolCall(name, args));
        } catch (Exception e) {
            return ModelTurn.retry(raw, retryNotice("模型返回的工具 JSON 格式不正确"), "malformed_tool_json");
        }
    }

    private static ModelTurn parseXmlTool(String raw, String attrsText, String body) {
        Map<String, Object> attrs = parseAttrs(attrsText);
        Object nameValue = attrs.remove("name");
        String name = nameValue == null ? "" : String.valueOf(nameValue).trim();
        if (name.isBlank()) {
            return ModelTurn.retry(raw, retryNotice("工具 payload 缺少工具名"), "missing_tool_name");
        }

        String xmlBody = body == null ? "" : body;
        Map<String, Object> args = new LinkedHashMap<>(attrs);
        for (String key : new String[]{"content", "old_text", "new_text", "command", "task", "pattern", "path"}) {
            String extracted = extractRaw(xmlBody, key);
            if (extracted != null) {
                args.put(key, extracted);
            }
        }

        String bodyText = xmlBody.strip();
        if ("write_file".equals(name) && !args.containsKey("content") && !bodyText.isBlank()) {
            args.put("content", bodyText);
        }
        if ("delegate".equals(name) && !args.containsKey("task") && !bodyText.isBlank()) {
            args.put("task", bodyText);
        }
        if ("patch_file".equals(name) && !args.containsKey("new_text") && !bodyText.isBlank()) {
            args.put("new_text", bodyText);
        }
        return ModelTurn.toolCall(raw, new ToolCall(name, args));
    }

    private static Map<String, Object> parseAttrs(String text) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        String attrText = text == null ? "" : text;
        Matcher matcher = ATTR_PATTERN.matcher(attrText);
        while (matcher.find()) {
            attrs.put(matcher.group(1), matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
        }
        return attrs;
    }

    private static String extractRaw(String text, String tag) {
        String startTag = "<" + tag + ">";
        String endTag = "</" + tag + ">";
        int start = text.indexOf(startTag);
        if (start < 0) {
            return null;
        }
        start += startTag.length();
        int end = text.indexOf(endTag, start);
        if (end < 0) {
            return text.substring(start);
        }
        return text.substring(start, end);
    }

    static String retryNotice(String problem) {
        return "运行时提示：" + problem + "。请回复一个有效的 <tool> 调用，或一个非空的 <final> 答案。";
    }
}
