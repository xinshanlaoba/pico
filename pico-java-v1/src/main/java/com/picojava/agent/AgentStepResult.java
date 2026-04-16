package com.picojava.agent;

public record AgentStepResult(
        int stepNumber,
        String outcome,
        ModelTurn modelTurn,
        ToolExecutionResult toolExecutionResult,
        FinalAnswer finalAnswer,
        String feedbackMessage
) {
    public static AgentStepResult finalAnswer(int stepNumber, ModelTurn modelTurn, FinalAnswer finalAnswer) {
        return new AgentStepResult(stepNumber, "final_answer", modelTurn, null, finalAnswer, finalAnswer.content());
    }

    public static AgentStepResult toolResult(int stepNumber, ModelTurn modelTurn, ToolExecutionResult toolExecutionResult) {
        return new AgentStepResult(stepNumber, "tool_result", modelTurn, toolExecutionResult, null, toolExecutionResult.feedbackMessage());
    }

    public static AgentStepResult retry(int stepNumber, ModelTurn modelTurn) {
        return new AgentStepResult(stepNumber, "retry", modelTurn, null, null, modelTurn.retryMessage());
    }

    public boolean isFinalAnswer() {
        return finalAnswer != null;
    }
}
