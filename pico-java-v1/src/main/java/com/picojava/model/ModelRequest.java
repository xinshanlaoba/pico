package com.picojava.model;

public record ModelRequest(
        String prompt,
        int maxNewTokens
) {
    public ModelRequest {
        prompt = prompt == null ? "" : prompt;
        if (maxNewTokens <= 0) {
            maxNewTokens = 1;
        }
    }
}
