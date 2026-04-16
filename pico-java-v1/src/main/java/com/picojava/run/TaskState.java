package com.picojava.run;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.picojava.model.ModelUsage;
import com.picojava.common.TextUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TaskState {
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_STOPPED = "stopped";
    public static final String STATUS_FAILED = "failed";

    public static final String STOP_REASON_FINAL_ANSWER_RETURNED = "final_answer_returned";
    public static final String STOP_REASON_STEP_LIMIT_REACHED = "step_limit_reached";
    public static final String STOP_REASON_ERROR = "error";

    private static final DateTimeFormatter ID_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private String runId;
    private String taskId;
    private String sessionId;
    private String workspaceRoot;
    private String parentRunId = "";
    private int delegateDepth;
    private String userInput;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant endedAt;
    private String status = STATUS_RUNNING;
    private String stopReason = "";
    private int stepCount;
    private int modelCallCount;
    private int toolCallCount;
    private String finalAnswer = "";
    private String error = "";
    private List<ChildRun> childRuns = new ArrayList<>();
    private List<Step> steps = new ArrayList<>();

    public TaskState() {}

    public static TaskState create(String sessionId, String workspaceRoot, String userInput) {
        return create(sessionId, workspaceRoot, userInput, "", 0);
    }

    public static TaskState create(String sessionId, String workspaceRoot, String userInput,
                                   String parentRunId, int delegateDepth) {
        TaskState state = new TaskState();
        state.runId = newId("run");
        state.taskId = newId("task");
        state.sessionId = defaultString(sessionId);
        state.workspaceRoot = defaultString(workspaceRoot);
        state.parentRunId = defaultString(parentRunId);
        state.delegateDepth = Math.max(delegateDepth, 0);
        state.userInput = defaultString(userInput);
        state.startedAt = Instant.now();
        return state;
    }

    public Step beginStep() {
        Step step = new Step(steps.size() + 1);
        steps.add(step);
        stepCount = steps.size();
        return step;
    }

    public void recordModelCall(Step step, ModelCall modelCall) {
        step.recordModelCall(modelCall);
        modelCallCount += 1;
    }

    public void recordToolCall(Step step, ToolCall toolCall) {
        step.recordToolCall(toolCall);
        toolCallCount += 1;
    }

    public void recordChildRun(ChildRun childRun) {
        if (childRun == null || childRun.getRunId().isBlank()) {
            return;
        }
        childRuns.removeIf(existing -> existing.getRunId().equals(childRun.getRunId()));
        childRuns.add(childRun);
    }

    public void finishSuccess(String answer) {
        status = STATUS_COMPLETED;
        stopReason = STOP_REASON_FINAL_ANSWER_RETURNED;
        finalAnswer = defaultString(answer);
        endedAt = Instant.now();
    }

    public void stopStepLimit(String answer) {
        status = STATUS_STOPPED;
        stopReason = STOP_REASON_STEP_LIMIT_REACHED;
        finalAnswer = defaultString(answer);
        endedAt = Instant.now();
    }

    public void fail(String errorMessage, String answer) {
        status = STATUS_FAILED;
        stopReason = STOP_REASON_ERROR;
        error = defaultString(errorMessage);
        if (answer != null) {
            finalAnswer = answer;
        }
        endedAt = Instant.now();
    }

    public void markEndedIfMissing() {
        if (endedAt == null) {
            endedAt = Instant.now();
        }
    }

    public long durationMs() {
        if (startedAt == null || endedAt == null) {
            return 0L;
        }
        return Duration.between(startedAt, endedAt).toMillis();
    }

    public String getRunId() {
        return runId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public String getParentRunId() {
        return parentRunId;
    }

    public int getDelegateDepth() {
        return delegateDepth;
    }

    public String getUserInput() {
        return userInput;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getStopReason() {
        return stopReason;
    }

    public int getStepCount() {
        return stepCount;
    }

    public int getModelCallCount() {
        return modelCallCount;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public String getError() {
        return error;
    }

    public List<ChildRun> getChildRuns() {
        return childRuns;
    }

    public List<Step> getSteps() {
        return steps;
    }

    private static String newId(String prefix) {
        return prefix + "-" + ID_FORMATTER.format(Instant.now()) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Step {
        private int index;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant startedAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant endedAt;
        private String status = STATUS_RUNNING;
        private ModelCall modelCall;
        private ToolCall toolCall;
        private String finalAnswer = "";
        private String error = "";

        public Step() {}

        private Step(int index) {
            this.index = index;
            this.startedAt = Instant.now();
        }

        public void recordModelCall(ModelCall modelCall) {
            this.modelCall = modelCall;
        }

        public void recordToolCall(ToolCall toolCall) {
            this.toolCall = toolCall;
        }

        public void finish(String status) {
            this.status = defaultString(status);
            if (endedAt == null) {
                endedAt = Instant.now();
            }
        }

        public void finishWithAnswer(String answer) {
            finalAnswer = defaultString(answer);
            finish(STATUS_COMPLETED);
        }

        public void fail(String status, String errorMessage) {
            String normalizedStatus = defaultString(status);
            this.status = normalizedStatus.isBlank() ? STATUS_FAILED : normalizedStatus;
            this.error = defaultString(errorMessage);
            endedAt = Instant.now();
        }

        public int getIndex() {
            return index;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        public Instant getEndedAt() {
            return endedAt;
        }

        public String getStatus() {
            return status;
        }

        public ModelCall getModelCall() {
            return modelCall;
        }

        public ToolCall getToolCall() {
            return toolCall;
        }

        public String getFinalAnswer() {
            return finalAnswer;
        }

        public String getError() {
            return error;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ModelCall {
        private String provider;
        private String model;
        private int maxNewTokens;
        private int promptChars;
        private String prompt = "";
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant startedAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant endedAt;
        private String rawResponse = "";
        private String responseKind = "";
        private String stopReason = "";
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;
        private String error = "";

        public ModelCall() {}

        public ModelCall(String provider, String model, int maxNewTokens, String prompt) {
            this.provider = defaultString(provider);
            this.model = defaultString(model);
            this.maxNewTokens = maxNewTokens;
            this.promptChars = prompt == null ? 0 : prompt.length();
            this.prompt = TextUtils.clip(defaultString(prompt), 8000);
            this.startedAt = Instant.now();
        }

        public void complete(String rawResponse, String responseKind) {
            complete(rawResponse, responseKind, "", null);
        }

        public void complete(String rawResponse, String responseKind, String stopReason, ModelUsage usage) {
            this.rawResponse = TextUtils.clip(defaultString(rawResponse), 8000);
            this.responseKind = defaultString(responseKind);
            this.stopReason = defaultString(stopReason);
            if (usage != null) {
                this.inputTokens = usage.inputTokens();
                this.outputTokens = usage.outputTokens();
                this.totalTokens = usage.totalTokens();
            }
            this.endedAt = Instant.now();
        }

        public void fail(String errorMessage) {
            this.error = defaultString(errorMessage);
            this.endedAt = Instant.now();
        }

        public long durationMs() {
            if (startedAt == null || endedAt == null) {
                return 0L;
            }
            return Duration.between(startedAt, endedAt).toMillis();
        }

        public String getProvider() {
            return provider;
        }

        public String getModel() {
            return model;
        }

        public int getMaxNewTokens() {
            return maxNewTokens;
        }

        public int getPromptChars() {
            return promptChars;
        }

        public String getPrompt() {
            return prompt;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        public Instant getEndedAt() {
            return endedAt;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public String getResponseKind() {
            return responseKind;
        }

        public String getStopReason() {
            return stopReason;
        }

        public Integer getInputTokens() {
            return inputTokens;
        }

        public Integer getOutputTokens() {
            return outputTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        public String getError() {
            return error;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ChildRun {
        private String runId = "";
        private String taskId = "";
        private String task = "";
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant startedAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant endedAt;
        private String status = "";
        private String stopReason = "";
        private String finalAnswer = "";
        private String error = "";

        public ChildRun() {}

        public static ChildRun fromDelegate(String runId, String taskId, String task, Instant startedAt, Instant endedAt,
                                            String status, String stopReason, String finalAnswer, String error) {
            ChildRun childRun = new ChildRun();
            childRun.runId = defaultString(runId);
            childRun.taskId = defaultString(taskId);
            childRun.task = defaultString(task);
            childRun.startedAt = startedAt;
            childRun.endedAt = endedAt;
            childRun.status = defaultString(status);
            childRun.stopReason = defaultString(stopReason);
            childRun.finalAnswer = defaultString(finalAnswer);
            childRun.error = defaultString(error);
            return childRun;
        }

        public String getRunId() {
            return runId;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getTask() {
            return task;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        public Instant getEndedAt() {
            return endedAt;
        }

        public String getStatus() {
            return status;
        }

        public String getStopReason() {
            return stopReason;
        }

        public String getFinalAnswer() {
            return finalAnswer;
        }

        public String getError() {
            return error;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ToolCall {
        private String name;
        private Map<String, Object> args = new LinkedHashMap<>();
        private boolean risky;
        private Boolean approved;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant startedAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant endedAt;
        private String status = "pending";
        private String result = "";
        private String error = "";

        public ToolCall() {}

        public ToolCall(String name, Map<String, Object> args, boolean risky) {
            this.name = defaultString(name);
            if (args != null) {
                this.args.putAll(args);
            }
            this.risky = risky;
            this.startedAt = Instant.now();
        }

        public void markApproved(boolean approved) {
            this.approved = approved;
        }

        public void complete(String result) {
            this.status = "completed";
            this.result = TextUtils.clip(defaultString(result), 8000);
            this.endedAt = Instant.now();
        }

        public void reject(String status, String errorMessage) {
            this.status = defaultString(status);
            this.error = defaultString(errorMessage);
            this.endedAt = Instant.now();
        }

        public void fail(String errorMessage) {
            this.status = "failed";
            this.error = defaultString(errorMessage);
            this.endedAt = Instant.now();
        }

        public long durationMs() {
            if (startedAt == null || endedAt == null) {
                return 0L;
            }
            return Duration.between(startedAt, endedAt).toMillis();
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getArgs() {
            return args;
        }

        public boolean isRisky() {
            return risky;
        }

        public Boolean getApproved() {
            return approved;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        public Instant getEndedAt() {
            return endedAt;
        }

        public String getStatus() {
            return status;
        }

        public String getResult() {
            return result;
        }

        public String getError() {
            return error;
        }
    }
}
