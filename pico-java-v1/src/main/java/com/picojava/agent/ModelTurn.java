package com.picojava.agent;

public final class ModelTurn {
    public enum Kind {
        FINAL_ANSWER,
        TOOL_CALL,
        RETRY
    }

    private final String rawResponse;
    private final Kind kind;
    private final FinalAnswer finalAnswer;
    private final ToolCall toolCall;
    private final String retryMessage;
    private final String retryReason;

    private ModelTurn(String rawResponse, Kind kind, FinalAnswer finalAnswer, ToolCall toolCall,
                      String retryMessage, String retryReason) {
        this.rawResponse = rawResponse == null ? "" : rawResponse;
        this.kind = kind;
        this.finalAnswer = finalAnswer;
        this.toolCall = toolCall;
        this.retryMessage = retryMessage == null ? "" : retryMessage;
        this.retryReason = retryReason == null ? "" : retryReason;
    }

    public static ModelTurn finalAnswer(String rawResponse, FinalAnswer finalAnswer) {
        return new ModelTurn(rawResponse, Kind.FINAL_ANSWER, finalAnswer, null, "", "");
    }

    public static ModelTurn toolCall(String rawResponse, ToolCall toolCall) {
        return new ModelTurn(rawResponse, Kind.TOOL_CALL, null, toolCall, "", "");
    }

    public static ModelTurn retry(String rawResponse, String retryMessage, String retryReason) {
        return new ModelTurn(rawResponse, Kind.RETRY, null, null, retryMessage, retryReason);
    }

    public String rawResponse() {
        return rawResponse;
    }

    public Kind kind() {
        return kind;
    }

    public FinalAnswer finalAnswer() {
        return finalAnswer;
    }

    public ToolCall toolCall() {
        return toolCall;
    }

    public String retryMessage() {
        return retryMessage;
    }

    public String retryReason() {
        return retryReason;
    }

    public boolean isFinalAnswer() {
        return kind == Kind.FINAL_ANSWER;
    }

    public boolean isToolCall() {
        return kind == Kind.TOOL_CALL;
    }

    public boolean isRetry() {
        return kind == Kind.RETRY;
    }
}
