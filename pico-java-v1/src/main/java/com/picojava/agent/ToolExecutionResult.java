package com.picojava.agent;

public record ToolExecutionResult(
        ToolCall toolCall,
        String status,
        String output,
        String error,
        boolean risky,
        Boolean approved
) {
    public static ToolExecutionResult success(ToolCall toolCall, String output, boolean risky, Boolean approved) {
        return new ToolExecutionResult(toolCall, "completed", output == null ? "" : output, "", risky, approved);
    }

    public static ToolExecutionResult rejected(ToolCall toolCall, String status, String error, boolean risky, Boolean approved) {
        return new ToolExecutionResult(toolCall, status, "", error == null ? "" : error, risky, approved);
    }

    public static ToolExecutionResult failed(ToolCall toolCall, String error, boolean risky, Boolean approved) {
        return new ToolExecutionResult(toolCall, "failed", "", error == null ? "" : error, risky, approved);
    }

    public boolean isSuccess() {
        return "completed".equals(status);
    }

    public String feedbackMessage() {
        if (isSuccess()) {
            return toolCall.name() + " -> " + output;
        }
        return error;
    }
}
