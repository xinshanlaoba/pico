package com.picojava.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.picojava.common.JsonUtils;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Duration;

public class OllamaModelClient extends AbstractHttpModelClient {
    private final String baseUrl;
    private final double temperature;
    private final double topP;

    public OllamaModelClient(String model, String host, double temperature, double topP, Duration timeout) {
        this(model, host, temperature, topP, timeout, ModelClientConfig.DEFAULT_MAX_RETRIES, null);
    }

    OllamaModelClient(String model, String host, double temperature, double topP,
                      Duration timeout, int maxRetries, HttpExecutor executor) {
        super(model, timeout, maxRetries, executor);
        this.baseUrl = normalizeBaseUrl(host);
        this.temperature = temperature;
        this.topP = topP;
    }

    @Override
    protected HttpRequest buildRequest(ModelRequest request) throws IOException {
        GenerateRequest payload = new GenerateRequest(
                model,
                request.prompt(),
                false,
                false,
                false,
                new Options(request.maxNewTokens(), temperature, topP)
        );
        return jsonRequestBuilder(baseUrl + "/api/generate")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.MAPPER.writeValueAsString(payload)))
                .build();
    }

    @Override
    protected ModelResponse parseResponse(String rawPayload) throws IOException {
        GenerateResponse response = JsonUtils.MAPPER.readValue(rawPayload, GenerateResponse.class);
        ModelUsage usage = new ModelUsage(response.promptEvalCount(), response.evalCount(), null);
        return new ModelResponse(response.response(), rawPayload, usage.hasAnyValue() ? usage : null, response.doneReason());
    }

    @Override
    public String providerName() {
        return "ollama";
    }

    private static String normalizeBaseUrl(String value) {
        String base = value == null || value.isBlank() ? ModelClientConfig.DEFAULT_OLLAMA_BASE_URL : value.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private record GenerateRequest(
            String model,
            String prompt,
            boolean stream,
            boolean raw,
            boolean think,
            Options options
    ) {}

    private record Options(
            @JsonProperty("num_predict") int numPredict,
            double temperature,
            @JsonProperty("top_p") double topP
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GenerateResponse(
            String response,
            @JsonProperty("done_reason") String doneReason,
            @JsonProperty("prompt_eval_count") Integer promptEvalCount,
            @JsonProperty("eval_count") Integer evalCount
    ) {
        private GenerateResponse {
            response = response == null ? "" : response;
            doneReason = doneReason == null ? "" : doneReason;
        }
    }
}
