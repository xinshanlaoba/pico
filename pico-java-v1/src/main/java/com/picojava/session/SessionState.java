package com.picojava.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SessionState {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String SESSION_TYPE = "pico-java-session";
    private static final int MAX_RECENT_RUNS = 10;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private String sessionType = SESSION_TYPE;
    private String id;
    private String workspaceRoot;
    private Instant createdAt;
    private Instant updatedAt;
    private List<MessageEntry> history = new ArrayList<>();
    private String distilledMemory = "";
    private Map<String, Object> memoryState = new LinkedHashMap<>();
    private RuntimeConfig runtimeConfig = new RuntimeConfig();
    private String latestRunId = "";
    private List<RunInfo> recentRuns = new ArrayList<>();

    public SessionState() {}

    public static SessionState create(String id, String workspaceRoot) {
        SessionState state = new SessionState();
        state.id = id;
        state.workspaceRoot = workspaceRoot;
        state.createdAt = Instant.now();
        state.updatedAt = Instant.now();
        state.normalize();
        return state;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void normalize() {
        if (schemaVersion <= 0) schemaVersion = CURRENT_SCHEMA_VERSION;
        if (sessionType == null || sessionType.isBlank()) sessionType = SESSION_TYPE;
        if (history == null) history = new ArrayList<>();
        if (distilledMemory == null) distilledMemory = "";
        if (memoryState == null) memoryState = new LinkedHashMap<>();
        if (runtimeConfig == null) runtimeConfig = new RuntimeConfig();
        runtimeConfig.normalize();
        if (recentRuns == null) recentRuns = new ArrayList<>();
        if (latestRunId == null) latestRunId = "";
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (workspaceRoot == null) workspaceRoot = "";
        if (id == null) id = "";
    }

    public void updateRuntimeConfig(String approvalPolicy, int maxSteps, int maxNewTokens,
                                    Set<String> secretEnvNames, String providerName, String modelName) {
        normalize();
        runtimeConfig.setApprovalPolicy(blankToEmpty(approvalPolicy).toLowerCase());
        runtimeConfig.setMaxSteps(maxSteps);
        runtimeConfig.setMaxNewTokens(maxNewTokens);
        runtimeConfig.setProviderName(blankToEmpty(providerName));
        runtimeConfig.setModelName(blankToEmpty(modelName));
        List<String> names = secretEnvNames == null ? List.of() : secretEnvNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .map(String::toUpperCase)
                .distinct()
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        runtimeConfig.setSecretEnvNames(names);
    }

    public void recordRun(RunInfo runInfo) {
        normalize();
        if (runInfo == null || runInfo.getRunId().isBlank()) {
            return;
        }
        latestRunId = runInfo.getRunId();
        recentRuns.removeIf(existing -> runInfo.getRunId().equals(existing.getRunId()));
        recentRuns.add(0, runInfo);
        recentRuns.sort(Comparator.comparing(RunInfo::getEndedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RunInfo::getStartedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        if (recentRuns.size() > MAX_RECENT_RUNS) {
            recentRuns = new ArrayList<>(recentRuns.subList(0, MAX_RECENT_RUNS));
        }
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getSessionType() { return sessionType; }
    public void setSessionType(String sessionType) { this.sessionType = sessionType; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkspaceRoot() { return workspaceRoot; }
    public void setWorkspaceRoot(String workspaceRoot) { this.workspaceRoot = workspaceRoot; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<MessageEntry> getHistory() { return history; }
    public void setHistory(List<MessageEntry> history) { this.history = history; }
    public String getDistilledMemory() { return distilledMemory; }
    public void setDistilledMemory(String distilledMemory) { this.distilledMemory = distilledMemory; }
    public Map<String, Object> getMemoryState() { return memoryState; }
    public void setMemoryState(Map<String, Object> memoryState) { this.memoryState = memoryState; }
    public RuntimeConfig getRuntimeConfig() { return runtimeConfig; }
    public void setRuntimeConfig(RuntimeConfig runtimeConfig) { this.runtimeConfig = runtimeConfig; }
    public String getLatestRunId() { return latestRunId; }
    public void setLatestRunId(String latestRunId) { this.latestRunId = latestRunId; }
    public List<RunInfo> getRecentRuns() { return recentRuns; }
    public void setRecentRuns(List<RunInfo> recentRuns) { this.recentRuns = recentRuns; }

    public static class RuntimeConfig {
        private String approvalPolicy = "";
        private int maxSteps;
        private int maxNewTokens;
        private List<String> secretEnvNames = new ArrayList<>();
        private String providerName = "";
        private String modelName = "";

        public void normalize() {
            if (approvalPolicy == null) approvalPolicy = "";
            approvalPolicy = approvalPolicy.trim().toLowerCase();
            if (secretEnvNames == null) secretEnvNames = new ArrayList<>();
            if (providerName == null) providerName = "";
            if (modelName == null) modelName = "";
        }

        public String getApprovalPolicy() { return approvalPolicy; }
        public void setApprovalPolicy(String approvalPolicy) { this.approvalPolicy = approvalPolicy; }
        public int getMaxSteps() { return maxSteps; }
        public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }
        public int getMaxNewTokens() { return maxNewTokens; }
        public void setMaxNewTokens(int maxNewTokens) { this.maxNewTokens = maxNewTokens; }
        public List<String> getSecretEnvNames() { return secretEnvNames; }
        public void setSecretEnvNames(List<String> secretEnvNames) { this.secretEnvNames = secretEnvNames; }
        public String getProviderName() { return providerName; }
        public void setProviderName(String providerName) { this.providerName = providerName; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
    }

    public static class RunInfo {
        private String runId = "";
        private String taskId = "";
        private Instant startedAt;
        private Instant endedAt;
        private String status = "";
        private String stopReason = "";
        private String finalAnswer = "";
        private String error = "";

        public RunInfo() {}

        public static RunInfo fromTask(String runId, String taskId, Instant startedAt, Instant endedAt,
                                       String status, String stopReason, String finalAnswer, String error) {
            RunInfo info = new RunInfo();
            info.runId = blankToEmpty(runId);
            info.taskId = blankToEmpty(taskId);
            info.startedAt = startedAt;
            info.endedAt = endedAt;
            info.status = blankToEmpty(status);
            info.stopReason = blankToEmpty(stopReason);
            info.finalAnswer = blankToEmpty(finalAnswer);
            info.error = blankToEmpty(error);
            return info;
        }

        public String getRunId() { return runId; }
        public void setRunId(String runId) { this.runId = runId; }
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public Instant getStartedAt() { return startedAt; }
        public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
        public Instant getEndedAt() { return endedAt; }
        public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getStopReason() { return stopReason; }
        public void setStopReason(String stopReason) { this.stopReason = stopReason; }
        public String getFinalAnswer() { return finalAnswer; }
        public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
