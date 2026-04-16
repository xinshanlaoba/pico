package com.picojava.model;

import com.picojava.common.JsonUtils;
import com.picojava.tool.Tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ModelToolDefinition(
        String name,
        String description,
        String jsonSchema
) {
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*([A-Za-z_][A-Za-z0-9_]*)(?:\\s*=\\s*(.+))?\\s*"
    );

    public ModelToolDefinition {
        name = requireText(name, "Tool name cannot be blank");
        description = description == null ? "" : description.trim();
        jsonSchema = requireText(jsonSchema, "Tool schema cannot be blank");
    }

    public static List<ModelToolDefinition> fromTools(Collection<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<ModelToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : tools) {
            if (tool != null) {
                definitions.add(fromTool(tool));
            }
        }
        return List.copyOf(definitions);
    }

    public static ModelToolDefinition fromTool(Tool tool) {
        String description = tool.description();
        if (tool.risky()) {
            description = description + " Approval is required before execution.";
        }
        return new ModelToolDefinition(tool.name(), description, toJsonSchema(tool.schema()));
    }

    static String toJsonSchema(String schemaDsl) {
        Map<String, Object> schema = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        schema.put("type", "object");
        schema.put("additionalProperties", false);

        String body = normalizeSchemaDsl(schemaDsl);
        if (!body.isBlank()) {
            for (String token : splitFields(body)) {
                Matcher matcher = FIELD_PATTERN.matcher(token);
                if (!matcher.matches()) {
                    continue;
                }

                String fieldName = matcher.group(1);
                String fieldType = matcher.group(2);
                String defaultValue = matcher.group(3);
                Object parsedDefault = parseDefault(fieldType, defaultValue);

                Map<String, Object> property = new LinkedHashMap<>();
                property.put("type", jsonType(fieldType));
                if (parsedDefault != null) {
                    property.put("default", parsedDefault);
                } else {
                    required.add(fieldName);
                }
                properties.put(fieldName, property);
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        try {
            return JsonUtils.MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build tool schema JSON", e);
        }
    }

    private static String normalizeSchemaDsl(String schemaDsl) {
        String text = schemaDsl == null ? "" : schemaDsl.trim();
        if (text.startsWith("{") && text.endsWith("}")) {
            text = text.substring(1, text.length() - 1);
        }
        return text.trim();
    }

    private static List<String> splitFields(String body) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }

            if (ch == ',' && !inSingleQuote && !inDoubleQuote) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString().trim());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString().trim());
        }
        return tokens;
    }

    private static String jsonType(String fieldType) {
        return switch (fieldType.toLowerCase()) {
            case "int", "integer", "long" -> "integer";
            case "float", "double", "number" -> "number";
            case "bool", "boolean" -> "boolean";
            default -> "string";
        };
    }

    private static Object parseDefault(String fieldType, String rawDefault) {
        if (rawDefault == null || rawDefault.isBlank()) {
            return null;
        }

        String value = rawDefault.trim();
        if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\""))) {
            value = value.substring(1, value.length() - 1);
        }

        return switch (fieldType.toLowerCase()) {
            case "int", "integer", "long" -> Integer.parseInt(value);
            case "float", "double", "number" -> Double.parseDouble(value);
            case "bool", "boolean" -> Boolean.parseBoolean(value);
            default -> value;
        };
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
