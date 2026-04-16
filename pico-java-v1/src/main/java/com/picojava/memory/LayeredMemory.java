package com.picojava.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.picojava.agent.ToolExecutionResult;
import com.picojava.common.JsonUtils;
import com.picojava.common.TextUtils;
import com.picojava.session.SessionState;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LayeredMemory {
    private static final int MAX_RECENT_FILES = 8;
    private static final int MAX_RECENT_TOOL_RESULTS = 6;
    private static final int TASK_SUMMARY_LIMIT = 300;
    private static final int SUMMARY_MEMORY_LIMIT = 1200;
    private static final int TOOL_ARGS_LIMIT = 220;
    private static final int TOOL_RESULT_LIMIT = 320;

    private final Path workspaceRoot;
    private final MemoryState state;

    public LayeredMemory(Path workspaceRoot, Map<String, Object> persistedState, String distilledSummary) {
        this.workspaceRoot = workspaceRoot;
        this.state = loadState(persistedState, distilledSummary);
    }

    public void syncToSession(SessionState session) {
        state.normalize();
        session.setDistilledMemory(state.getSummaryMemory());
        session.setMemoryState(JsonUtils.MAPPER.convertValue(state, new TypeReference<>() {}));
    }

    public void mergeSessionState(SessionState session) {
        if (session == null) {
            return;
        }
        state.normalize();
        Map<String, Object> currentState = toMap();
        Map<String, Object> sessionState = session.getMemoryState() == null ? Map.of() : session.getMemoryState();
        if (currentState.equals(sessionState)
                && Objects.equals(state.getSummaryMemory(), session.getDistilledMemory() == null ? "" : session.getDistilledMemory())) {
            return;
        }
        MemoryState external = loadState(session.getMemoryState(), session.getDistilledMemory());
        if (state.getTaskSummary().isBlank() && !external.getTaskSummary().isBlank()) {
            state.setTaskSummary(external.getTaskSummary());
        }
        if (!external.getSummaryMemory().isBlank()) {
            state.setSummaryMemory(external.getSummaryMemory());
        }
        state.setRecentFiles(mergeRecentFiles(external.getRecentFiles(), state.getRecentFiles()));
        state.setRecentToolResults(mergeRecentToolResults(external.getRecentToolResults(), state.getRecentToolResults()));
        Map<String, Object> extensions = new LinkedHashMap<>(external.getExtensions());
        extensions.putAll(state.getExtensions());
        state.setExtensions(extensions);
    }

    public void clear() {
        state.setTaskSummary("");
        state.setSummaryMemory("");
        state.setRecentFiles(new ArrayList<>());
        state.setRecentToolResults(new ArrayList<>());
        state.setExtensions(new LinkedHashMap<>());
    }

    public void setTaskSummary(String summary) {
        state.setTaskSummary(TextUtils.clip(summary == null ? "" : summary.trim(), TASK_SUMMARY_LIMIT));
    }

    public void setSummaryMemory(String summary) {
        state.setSummaryMemory(TextUtils.clip(summary == null ? "" : summary.trim(), SUMMARY_MEMORY_LIMIT));
    }

    public void rememberFile(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return;
        }
        String canonical = canonicalPath(rawPath);
        if (canonical.isBlank()) {
            return;
        }
        List<String> files = new ArrayList<>(state.getRecentFiles());
        files.remove(canonical);
        files.add(canonical);
        if (files.size() > MAX_RECENT_FILES) {
            files = new ArrayList<>(files.subList(files.size() - MAX_RECENT_FILES, files.size()));
        }
        state.setRecentFiles(files);
    }

    public void recordToolResult(ToolExecutionResult toolExecutionResult) {
        if (toolExecutionResult == null) {
            return;
        }
        MemoryState.RecentToolResult entry = new MemoryState.RecentToolResult();
        entry.setToolName(toolExecutionResult.toolCall().name());
        entry.setStatus(toolExecutionResult.status());
        entry.setArgsSummary(TextUtils.clip(asJson(toolExecutionResult.toolCall().args()), TOOL_ARGS_LIMIT));
        String resultText = toolExecutionResult.isSuccess() ? toolExecutionResult.output() : toolExecutionResult.error();
        entry.setResultSummary(TextUtils.clip(resultText == null ? "" : resultText, TOOL_RESULT_LIMIT));
        entry.setCreatedAt(Instant.now());
        entry.normalize();

        List<MemoryState.RecentToolResult> items = new ArrayList<>(state.getRecentToolResults());
        items.add(entry);
        if (items.size() > MAX_RECENT_TOOL_RESULTS) {
            items = new ArrayList<>(items.subList(items.size() - MAX_RECENT_TOOL_RESULTS, items.size()));
        }
        state.setRecentToolResults(items);

        Object rawPath = toolExecutionResult.toolCall().args().get("path");
        if (rawPath != null) {
            rememberFile(String.valueOf(rawPath));
        }
    }

    public String renderMemoryText() {
        state.normalize();
        StringBuilder sb = new StringBuilder();
        sb.append("记忆：\n");
        sb.append("- 任务摘要：").append(state.getTaskSummary().isBlank() ? "-" : state.getTaskSummary()).append('\n');
        sb.append("- 摘要记忆：").append(state.getSummaryMemory().isBlank() ? "-" : state.getSummaryMemory()).append('\n');
        sb.append("- 最近文件：").append(state.getRecentFiles().isEmpty() ? "-" : String.join(", ", state.getRecentFiles())).append('\n');
        sb.append("- 最近工具结果：\n");
        if (state.getRecentToolResults().isEmpty()) {
            sb.append("  - 无");
        } else {
            for (MemoryState.RecentToolResult item : state.getRecentToolResults()) {
                sb.append("  - ").append(item.getToolName())
                        .append(" [").append(item.getStatus()).append("] ")
                        .append(item.getResultSummary())
                        .append('\n');
            }
            if (sb.charAt(sb.length() - 1) == '\n') {
                sb.setLength(sb.length() - 1);
            }
        }
        return sb.toString();
    }

    public String summarySectionText() {
        state.normalize();
        StringBuilder sb = new StringBuilder();
        sb.append("摘要记忆：\n");
        sb.append("- 任务摘要：").append(state.getTaskSummary().isBlank() ? "-" : state.getTaskSummary()).append('\n');
        sb.append("- 提炼摘要：").append(state.getSummaryMemory().isBlank() ? "-" : state.getSummaryMemory()).append('\n');
        sb.append("- 最近文件：").append(state.getRecentFiles().isEmpty() ? "-" : String.join(", ", state.getRecentFiles()));
        return sb.toString();
    }

    public String recentToolResultsSectionText() {
        state.normalize();
        StringBuilder sb = new StringBuilder();
        sb.append("最近工具结果：\n");
        if (state.getRecentToolResults().isEmpty()) {
            sb.append("- 无");
            return sb.toString();
        }
        for (MemoryState.RecentToolResult item : state.getRecentToolResults()) {
            sb.append("- ").append(item.getToolName())
                    .append(" [").append(item.getStatus()).append("] ")
                    .append(item.getArgsSummary())
                    .append(" => ")
                    .append(item.getResultSummary())
                    .append('\n');
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    public String workspaceSummarySectionText(String workspaceText, int limit) {
        return "工作区摘要：\n" + TextUtils.clip(workspaceText == null ? "" : workspaceText, limit);
    }

    public String getSummaryMemory() {
        return state.getSummaryMemory();
    }

    public MemoryState state() {
        state.normalize();
        return state;
    }

    public Map<String, Object> toMap() {
        state.normalize();
        return JsonUtils.MAPPER.convertValue(state, new TypeReference<>() {});
    }

    private MemoryState loadState(Map<String, Object> persistedState, String distilledSummary) {
        Map<String, Object> rawState = persistedState == null ? Map.of() : new LinkedHashMap<>(persistedState);
        MemoryState loaded;
        try {
            loaded = rawState.isEmpty()
                    ? new MemoryState()
                    : JsonUtils.MAPPER.convertValue(rawState, MemoryState.class);
        } catch (IllegalArgumentException e) {
            loaded = new MemoryState();
        }
        loaded.normalize();
        captureExtensionFields(rawState, loaded);
        if ((loaded.getSummaryMemory() == null || loaded.getSummaryMemory().isBlank()) && distilledSummary != null && !distilledSummary.isBlank()) {
            loaded.setSummaryMemory(TextUtils.clip(distilledSummary.trim(), SUMMARY_MEMORY_LIMIT));
        }
        return loaded;
    }

    private void captureExtensionFields(Map<String, Object> rawState, MemoryState loaded) {
        if (rawState == null || rawState.isEmpty()) {
            return;
        }
        Map<String, Object> extensions = new LinkedHashMap<>(loaded.getExtensions());
        for (Map.Entry<String, Object> entry : rawState.entrySet()) {
            String key = entry.getKey();
            if (key == null || isKnownKey(key)) {
                continue;
            }
            extensions.putIfAbsent(key, entry.getValue());
        }
        loaded.setExtensions(extensions);
    }

    private boolean isKnownKey(String key) {
        return "taskSummary".equals(key)
                || "summaryMemory".equals(key)
                || "recentFiles".equals(key)
                || "recentToolResults".equals(key)
                || "extensions".equals(key);
    }

    private List<String> mergeRecentFiles(List<String> older, List<String> newer) {
        List<String> merged = new ArrayList<>();
        for (String item : older) {
            if (item != null && !item.isBlank() && !merged.contains(item)) {
                merged.add(item);
            }
        }
        for (String item : newer) {
            if (item != null && !item.isBlank()) {
                merged.remove(item);
                merged.add(item);
            }
        }
        if (merged.size() > MAX_RECENT_FILES) {
            return new ArrayList<>(merged.subList(merged.size() - MAX_RECENT_FILES, merged.size()));
        }
        return merged;
    }

    private List<MemoryState.RecentToolResult> mergeRecentToolResults(List<MemoryState.RecentToolResult> older,
                                                                      List<MemoryState.RecentToolResult> newer) {
        List<MemoryState.RecentToolResult> merged = new ArrayList<>();
        if (older != null) {
            merged.addAll(older);
        }
        if (newer != null) {
            merged.addAll(newer);
        }
        if (merged.size() > MAX_RECENT_TOOL_RESULTS) {
            return new ArrayList<>(merged.subList(merged.size() - MAX_RECENT_TOOL_RESULTS, merged.size()));
        }
        return merged;
    }

    private String canonicalPath(String rawPath) {
        try {
            Path path = Path.of(rawPath);
            Path resolved = path.isAbsolute() ? path.normalize() : workspaceRoot.resolve(path).normalize();
            if (resolved.startsWith(workspaceRoot)) {
                return workspaceRoot.relativize(resolved).toString().replace('\\', '/');
            }
            return resolved.toString().replace('\\', '/');
        } catch (Exception e) {
            return rawPath.replace('\\', '/');
        }
    }

    private String asJson(Map<String, Object> value) {
        try {
            return JsonUtils.MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
