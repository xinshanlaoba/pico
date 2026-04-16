package com.picojava.model;

public record ModelUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens
) {
    public ModelUsage {
        if (totalTokens == null && inputTokens != null && outputTokens != null) {
            totalTokens = inputTokens + outputTokens;
        }
    }

    public boolean hasAnyValue() {
        return inputTokens != null || outputTokens != null || totalTokens != null;
    }
}
