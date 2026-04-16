package com.picojava.model;

import java.io.IOException;

public interface ModelClient {
    String complete(String prompt, int maxNewTokens) throws IOException, InterruptedException;

    default ModelResponse completeResponse(String prompt, int maxNewTokens) throws IOException, InterruptedException {
        return ModelResponse.text(complete(prompt, maxNewTokens));
    }

    default ModelResponse completeResponse(ModelRequest request) throws IOException, InterruptedException {
        return completeResponse(request.prompt(), request.maxNewTokens());
    }

    default boolean supportsNativeToolCalling() {
        return false;
    }

    String providerName();

    String modelName();
}
