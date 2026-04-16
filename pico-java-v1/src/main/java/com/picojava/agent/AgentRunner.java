package com.picojava.agent;

import com.picojava.common.TextUtils;
import com.picojava.model.ModelResponse;
import com.picojava.run.ReportWriter;
import com.picojava.run.TaskState;
import com.picojava.run.TraceEvent;
import com.picojava.run.TraceWriter;
import com.picojava.tool.Tool;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class AgentRunner {
    private final Pico pico;
    AgentRunner(Pico pico) {
        this.pico = pico;
    }

    String run(String userMessage) throws Exception {
        TaskState taskState = TaskState.create(
                pico.session().getId(),
                pico.workspace().repoRoot().toString(),
                userMessage,
                pico.parentRunId(),
                pico.delegateDepth()
        );
        TraceWriter traceWriter = pico.runStore().traceWriter(taskState.getRunId());
        ReportWriter reportWriter = pico.runStore().reportWriter(taskState.getRunId());
        Exception failure = null;
        String finalAnswer = null;

        pico.runStore().startRun(taskState);
        pico.attachTaskState(taskState);
        emitTrace(traceWriter, taskState, null, "run_started", runStartedPayload(taskState));
        pico.appendHistory("user", userMessage);
        pico.startTask(userMessage);

        try {
            for (int stepNumber = 1; stepNumber <= pico.maxSteps(); stepNumber++) {
                AgentStepResult stepResult = runStep(taskState, traceWriter, userMessage, stepNumber);
                if (stepResult.isFinalAnswer()) {
                    finalAnswer = stepResult.finalAnswer().content();
                    break;
                }
            }

            if (finalAnswer == null) {
                finalAnswer = "已达到步骤上限，尚未生成最终答案。";
                pico.appendHistory("assistant", finalAnswer);
                taskState.stopStepLimit(finalAnswer);
                pico.runStore().writeTaskState(taskState);
                emitTrace(traceWriter, taskState, null, "step_limit_reached", Map.of("message", finalAnswer));
            }
        } catch (Exception e) {
            failure = e;
            taskState.fail(describeException(e), finalAnswer);
            pico.runStore().writeTaskState(taskState);
            emitTrace(traceWriter, taskState, null, "run_error", runErrorPayload(taskState));
        } finally {
            taskState.markEndedIfMissing();
            pico.recordCompletedRun(
                    taskState.getRunId(),
                    taskState.getTaskId(),
                    taskState.getStatus(),
                    taskState.getStopReason(),
                    taskState.getFinalAnswer(),
                    taskState.getError(),
                    taskState.getStartedAt(),
                    taskState.getEndedAt()
            );
            try {
                pico.saveSession();
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                    taskState.fail(describeException(e), finalAnswer);
                } else {
                    failure.addSuppressed(e);
                }
            }

            Exception finalizationFailure = null;
            try {
                pico.runStore().writeTaskState(taskState);
            } catch (Exception e) {
                finalizationFailure = mergeFailure(finalizationFailure, e);
            }
            try {
                emitTrace(traceWriter, taskState, null, failure == null ? "run_finished" : "run_failed", runFinishedPayload(taskState));
            } catch (Exception e) {
                finalizationFailure = mergeFailure(finalizationFailure, e);
            }
            try {
                reportWriter.write(buildReport(taskState));
            } catch (Exception e) {
                finalizationFailure = mergeFailure(finalizationFailure, e);
            }
            if (finalizationFailure != null) {
                if (failure == null) {
                    failure = finalizationFailure;
                } else {
                    failure.addSuppressed(finalizationFailure);
                }
            }
            pico.clearTaskState(taskState);
        }

        if (failure != null) throw failure;
        return finalAnswer;
    }

    private AgentStepResult runStep(TaskState taskState, TraceWriter traceWriter, String userMessage, int stepNumber) throws Exception {
        TaskState.Step step = taskState.beginStep();
        pico.runStore().writeTaskState(taskState);
        emitTrace(traceWriter, taskState, step, "step_started", Map.of("status", step.getStatus(), "step_number", stepNumber));

        ContextManager.BuildResult buildResult = pico.contextManager().build(userMessage);
        String prompt = buildResult.prompt();
        Map<String, Object> promptPayload = new LinkedHashMap<>(buildResult.metadata());
        promptPayload.put("history_size", pico.session().getHistory().size());
        emitTrace(traceWriter, taskState, step, "prompt_built", promptPayload);

        TaskState.ModelCall modelCall = new TaskState.ModelCall(
                pico.modelClient().providerName(),
                pico.modelClient().modelName(),
                pico.maxNewTokens(),
                prompt
        );
        taskState.recordModelCall(step, modelCall);
        pico.runStore().writeTaskState(taskState);
        emitTrace(traceWriter, taskState, step, "model_call_started", modelCallStartedPayload(modelCall));

        ModelTurn turn;
        try {
            ModelResponse response = pico.modelClient().completeResponse(prompt, pico.maxNewTokens());
            String modelText = response.textContent();
            pico.appendHistory("assistant_raw", modelText);
            turn = ResponseParser.parse(modelText);
            String rawPayload = response.rawPayload().isBlank() ? modelText : response.rawPayload();
            modelCall.complete(rawPayload, turn.kind().name().toLowerCase(), response.stopReason(), response.usage());
            pico.runStore().writeTaskState(taskState);
            emitTrace(traceWriter, taskState, step, "model_call_completed", modelCallCompletedPayload(modelCall, turn));
        } catch (Exception e) {
            String error = describeException(e);
            modelCall.fail(error);
            step.fail("model_error", error);
            pico.runStore().writeTaskState(taskState);
            emitTrace(traceWriter, taskState, step, "model_call_failed", modelCallFailedPayload(modelCall, error));
            throw e;
        }

        emitTrace(traceWriter, taskState, step, "response_parsed", Map.of(
                "kind", turn.kind().name().toLowerCase(),
                "retry_reason", turn.retryReason()
        ));

        if (turn.isRetry()) {
            pico.appendHistory("runtime", turn.retryMessage());
            step.fail("retry", turn.retryMessage());
            pico.runStore().writeTaskState(taskState);
            emitTrace(traceWriter, taskState, step, "response_retry", Map.of(
                    "reason", turn.retryReason(),
                    "message", turn.retryMessage()
            ));
            emitTrace(traceWriter, taskState, step, "step_completed", stepCompletedPayload(step, "retry", null));
            return AgentStepResult.retry(stepNumber, turn);
        }

        if (turn.isFinalAnswer()) {
            FinalAnswer answer = turn.finalAnswer();
            pico.appendHistory("assistant", answer.content());
            step.finishWithAnswer(answer.content());
            taskState.finishSuccess(answer.content());
            pico.runStore().writeTaskState(taskState);
            emitTrace(traceWriter, taskState, step, "final_answer", Map.of("content", answer.content()));
            emitTrace(traceWriter, taskState, step, "step_completed", stepCompletedPayload(step, "final_answer", null));
            return AgentStepResult.finalAnswer(stepNumber, turn, answer);
        }

        ToolExecutionResult toolResult = executeToolCall(taskState, traceWriter, step, turn.toolCall());
        pico.rememberToolResult(toolResult);
        pico.appendHistory(historyRoleFor(toolResult), toolResult.feedbackMessage());
        if (toolResult.isSuccess()) {
            step.finish("tool_completed");
        } else {
            step.fail(toolResult.status(), toolResult.error());
        }
        pico.runStore().writeTaskState(taskState);
        emitTrace(traceWriter, taskState, step, "step_completed", stepCompletedPayload(step, "tool_result", toolResult));
        return AgentStepResult.toolResult(stepNumber, turn, toolResult);
    }

    private ToolExecutionResult executeToolCall(TaskState taskState, TraceWriter traceWriter, TaskState.Step step,
                                                ToolCall toolCall) throws IOException {
        Tool tool = pico.toolRegistry().find(toolCall.name());
        TaskState.ToolCall taskToolCall = new TaskState.ToolCall(toolCall.name(), toolCall.args(), tool != null && tool.risky());
        taskState.recordToolCall(step, taskToolCall);
        pico.runStore().writeTaskState(taskState);
        emitTrace(traceWriter, taskState, step, "tool_execution_started", toolExecutionStartedPayload(taskToolCall));

        if (tool == null) {
            String error = "未知工具：" + toolCall.name();
            taskToolCall.reject("unknown_tool", error);
            pico.runStore().writeTaskState(taskState);
            emitTrace(traceWriter, taskState, step, "tool_execution_rejected", toolExecutionPayload(taskToolCall));
            return ToolExecutionResult.rejected(toolCall, "unknown_tool", error, false, null);
        }

        try {
            tool.validate(pico, toolCall.args());
        } catch (Exception e) {
            String error = "工具 " + toolCall.name() + " 的参数无效：" + describeException(e);
            taskToolCall.reject("invalid_arguments", error);
            pico.runStore().writeTaskState(taskState);
            emitTrace(traceWriter, taskState, step, "tool_execution_rejected", toolExecutionPayload(taskToolCall));
            return ToolExecutionResult.rejected(toolCall, "invalid_arguments", error, tool.risky(), null);
        }

        boolean approved = !tool.risky() || pico.approveTool(toolCall, tool.risky());
        taskToolCall.markApproved(approved);
        if (tool.risky() && !approved) {
            String error = "工具 " + toolCall.name() + " 的执行审批被拒绝";
            taskToolCall.reject("approval_denied", error);
            pico.runStore().writeTaskState(taskState);
            emitTrace(traceWriter, taskState, step, "tool_execution_denied", toolExecutionPayload(taskToolCall));
            return ToolExecutionResult.rejected(toolCall, "approval_denied", error, true, false);
        }

        try {
            String output = tool.execute(pico, toolCall.args());
            taskToolCall.complete(output);
            pico.runStore().writeTaskState(taskState);
            emitTrace(traceWriter, taskState, step, "tool_execution_completed", toolExecutionPayload(taskToolCall));
            return ToolExecutionResult.success(toolCall, output, tool.risky(), approved);
        } catch (Exception e) {
            String error = "工具 " + toolCall.name() + " 执行失败：" + describeException(e);
            taskToolCall.fail(error);
            pico.runStore().writeTaskState(taskState);
            emitTrace(traceWriter, taskState, step, "tool_execution_failed", toolExecutionPayload(taskToolCall));
            return ToolExecutionResult.failed(toolCall, error, tool.risky(), approved);
        }
    }

    private String historyRoleFor(ToolExecutionResult toolResult) {
        if (toolResult.isSuccess()) {
            return "tool";
        }
        if ("approval_denied".equals(toolResult.status())) {
            return "tool_denied";
        }
        return "tool_error";
    }

    private void emitTrace(TraceWriter traceWriter, TaskState taskState, TaskState.Step step,
                           String eventType, Map<String, Object> payload) throws IOException {
        traceWriter.append(TraceEvent.of(
                taskState.getRunId(),
                eventType,
                step == null ? null : step.getIndex(),
                payload
        ));
    }

    private Map<String, Object> runStartedPayload(TaskState taskState) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskState.getTaskId());
        payload.put("session_id", taskState.getSessionId());
        payload.put("workspace_root", taskState.getWorkspaceRoot());
        payload.put("parent_run_id", taskState.getParentRunId());
        payload.put("delegate_depth", taskState.getDelegateDepth());
        payload.put("user_input", taskState.getUserInput());
        payload.put("approval_policy", pico.approvalPolicy().name().toLowerCase());
        payload.put("max_steps", pico.maxSteps());
        payload.put("max_new_tokens", pico.maxNewTokens());
        payload.put("provider", pico.modelClient().providerName());
        payload.put("model", pico.modelClient().modelName());
        return payload;
    }

    private Map<String, Object> modelCallStartedPayload(TaskState.ModelCall modelCall) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", modelCall.getProvider());
        payload.put("model", modelCall.getModel());
        payload.put("max_new_tokens", modelCall.getMaxNewTokens());
        payload.put("prompt_chars", modelCall.getPromptChars());
        payload.put("prompt", modelCall.getPrompt());
        return payload;
    }

    private Map<String, Object> modelCallCompletedPayload(TaskState.ModelCall modelCall, ModelTurn turn) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", modelCall.getProvider());
        payload.put("model", modelCall.getModel());
        payload.put("response_kind", turn.kind().name().toLowerCase());
        payload.put("retry_reason", turn.retryReason());
        payload.put("stop_reason", modelCall.getStopReason());
        payload.put("input_tokens", modelCall.getInputTokens());
        payload.put("output_tokens", modelCall.getOutputTokens());
        payload.put("total_tokens", modelCall.getTotalTokens());
        payload.put("raw_response", modelCall.getRawResponse());
        payload.put("duration_ms", modelCall.durationMs());
        return payload;
    }

    private Map<String, Object> modelCallFailedPayload(TaskState.ModelCall modelCall, String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", modelCall.getProvider());
        payload.put("model", modelCall.getModel());
        payload.put("error", error);
        payload.put("duration_ms", modelCall.durationMs());
        return payload;
    }

    private Map<String, Object> toolExecutionStartedPayload(TaskState.ToolCall toolCall) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", toolCall.getName());
        payload.put("args", toolCall.getArgs());
        payload.put("risky", toolCall.isRisky());
        return payload;
    }

    private Map<String, Object> toolExecutionPayload(TaskState.ToolCall toolCall) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", toolCall.getName());
        payload.put("args", toolCall.getArgs());
        payload.put("risky", toolCall.isRisky());
        payload.put("approved", toolCall.getApproved());
        payload.put("status", toolCall.getStatus());
        payload.put("result", toolCall.getResult());
        payload.put("error", toolCall.getError());
        payload.put("duration_ms", toolCall.durationMs());
        return payload;
    }

    private Map<String, Object> stepCompletedPayload(TaskState.Step step, String outcome, ToolExecutionResult toolResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outcome", outcome);
        payload.put("status", step.getStatus());
        payload.put("final_answer", step.getFinalAnswer());
        payload.put("error", step.getError());
        if (toolResult != null) {
            payload.put("tool_status", toolResult.status());
            payload.put("tool_name", toolResult.toolCall().name());
        }
        return payload;
    }

    private Map<String, Object> runErrorPayload(TaskState taskState) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", taskState.getStatus());
        payload.put("stop_reason", taskState.getStopReason());
        payload.put("error", taskState.getError());
        payload.put("step_count", taskState.getStepCount());
        payload.put("model_call_count", taskState.getModelCallCount());
        payload.put("tool_call_count", taskState.getToolCallCount());
        payload.put("child_run_count", taskState.getChildRuns().size());
        return payload;
    }

    private Map<String, Object> runFinishedPayload(TaskState taskState) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", taskState.getStatus());
        payload.put("stop_reason", taskState.getStopReason());
        payload.put("step_count", taskState.getStepCount());
        payload.put("model_call_count", taskState.getModelCallCount());
        payload.put("tool_call_count", taskState.getToolCallCount());
        payload.put("child_run_count", taskState.getChildRuns().size());
        payload.put("duration_ms", taskState.durationMs());
        payload.put("final_answer", taskState.getFinalAnswer());
        payload.put("error", taskState.getError());
        return payload;
    }

    private Map<String, Object> buildReport(TaskState taskState) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("run_id", taskState.getRunId());
        report.put("task_id", taskState.getTaskId());
        report.put("session_id", taskState.getSessionId());
        report.put("workspace_root", taskState.getWorkspaceRoot());
        report.put("parent_run_id", taskState.getParentRunId());
        report.put("delegate_depth", taskState.getDelegateDepth());
        report.put("status", taskState.getStatus());
        report.put("stop_reason", taskState.getStopReason());
        report.put("started_at", taskState.getStartedAt());
        report.put("ended_at", taskState.getEndedAt());
        report.put("duration_ms", taskState.durationMs());
        report.put("user_input", taskState.getUserInput());
        report.put("provider", pico.modelClient().providerName());
        report.put("model", pico.modelClient().modelName());
        report.put("step_count", taskState.getStepCount());
        report.put("model_call_count", taskState.getModelCallCount());
        report.put("tool_call_count", taskState.getToolCallCount());
        report.put("child_runs", taskState.getChildRuns());
        report.put("final_answer", taskState.getFinalAnswer());
        report.put("error", taskState.getError());
        report.put("steps", taskState.getSteps());
        report.put("task_state", taskState);
        report.put("history_size", pico.session().getHistory().size());
        return report;
    }

    private String describeException(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + TextUtils.clip(message, 4000);
    }

    private Exception mergeFailure(Exception current, Exception next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }
}
