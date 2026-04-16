package com.picojava.model;

import java.io.IOException;

public interface ModelClient {
    String complete(String prompt, int maxNewTokens) throws IOException, InterruptedException;

    default ModelResponse completeResponse(String prompt, int maxNewTokens) throws IOException, InterruptedException {
        return ModelResponse.text(complete(prompt, maxNewTokens));
    }

    String providerName();

    String modelName();
}
