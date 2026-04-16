package com.picojava.model;

public record ModelResponse(
        String textContent,
        String rawPayload,
        ModelUsage usage,
        String stopReason
) {
    public ModelResponse {
        textContent = textContent == null ? "" : textContent;
        rawPayload = rawPayload == null ? "" : rawPayload;
        stopReason = stopReason == null ? "" : stopReason;
    }

    public static ModelResponse text(String textContent) {
        return new ModelResponse(textContent, "", null, "");
    }
}
