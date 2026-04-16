package com.picojava.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.picojava.common.JsonUtils;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;

public class AnthropicCompatibleModelClient extends AbstractHttpModelClient {
    private final String baseUrl;
    private final String apiKey;
    private final double temperature;

    public AnthropicCompatibleModelClient(String model, String baseUrl, String apiKey, double temperature, Duration timeout) {
        this(model, baseUrl, apiKey, temperature, timeout, ModelClientConfig.DEFAULT_MAX_RETRIES, null);
    }

    AnthropicCompatibleModelClient(String model, String baseUrl, String apiKey, double temperature,
                                   Duration timeout, int maxRetries, HttpExecutor executor) {
        super(model, timeout, maxRetries, executor);
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.temperature = temperature;
    }

    @Override
    protected HttpRequest buildRequest(ModelRequest request) throws IOException {
        MessagesRequest payload = new MessagesRequest(
                model,
                request.maxNewTokens(),
                temperature,
                List.of(new Message("user", request.prompt()))
        );
        HttpRequest.Builder builder = jsonRequestBuilder(baseUrl + "/messages")
                .header("anthropic-version", "2023-06-01");
        if (!apiKey.isBlank()) {
            builder.header("x-api-key", apiKey);
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(JsonUtils.MAPPER.writeValueAsString(payload))).build();
    }

    @Override
    protected ModelResponse parseResponse(String rawPayload) throws IOException {
        MessagesResponse response = JsonUtils.MAPPER.readValue(rawPayload, MessagesResponse.class);
        String text = "";
        if (response.content() != null) {
            StringBuilder sb = new StringBuilder();
            for (ContentItem item : response.content()) {
                if (item != null && item.text() != null && !item.text().isBlank()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(item.text());
                }
            }
            text = sb.toString();
        }
        ModelUsage usage = response.usage() == null
                ? null
                : new ModelUsage(response.usage().inputTokens(), response.usage().outputTokens(), null);
        return new ModelResponse(text, rawPayload, usage != null && usage.hasAnyValue() ? usage : null, response.stopReason());
    }

    @Override
    public String providerName() {
        return "anthropic";
    }

    private static String normalizeBaseUrl(String value) {
        String base = value == null || value.isBlank() ? ModelClientConfig.DEFAULT_ANTHROPIC_BASE_URL : value.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.endsWith("/v1")) {
            base += "/v1";
        }
        return base;
    }

    private record MessagesRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature,
            List<Message> messages
    ) {}

    private record Message(
            String role,
            String content
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MessagesResponse(
            List<ContentItem> content,
            Usage usage,
            @JsonProperty("stop_reason") String stopReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentItem(
            String type,
            String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens
    ) {}
}
