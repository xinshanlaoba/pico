package com.picojava.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.picojava.common.JsonUtils;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;

public class OpenAiCompatibleModelClient extends AbstractHttpModelClient {
    private final String baseUrl;
    private final String apiKey;
    private final double temperature;

    public OpenAiCompatibleModelClient(String model, String baseUrl, String apiKey, double temperature, Duration timeout) {
        this(model, baseUrl, apiKey, temperature, timeout, ModelClientConfig.DEFAULT_MAX_RETRIES, null);
    }

    OpenAiCompatibleModelClient(String model, String baseUrl, String apiKey, double temperature,
                                Duration timeout, int maxRetries, HttpExecutor executor) {
        super(model, timeout, maxRetries, executor);
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.temperature = temperature;
    }

    @Override
    protected HttpRequest buildRequest(ModelRequest request) throws IOException {
        ResponsesRequest payload = ResponsesRequest.from(model, request.prompt(), request.maxNewTokens(), temperature);
        HttpRequest.Builder builder = jsonRequestBuilder(baseUrl + "/responses");
        if (!apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(JsonUtils.MAPPER.writeValueAsString(payload))).build();
    }

    @Override
    protected ModelResponse parseResponse(String rawPayload) throws IOException {
        ResponsesResponse response = JsonUtils.MAPPER.readValue(rawPayload, ResponsesResponse.class);
        String text = extractText(response);
        ModelUsage usage = extractUsage(response);
        String stopReason = firstNonBlank(
                response.stopReason(),
                response.incompleteDetails() == null ? null : response.incompleteDetails().reason(),
                firstChoiceStopReason(response),
                response.status()
        );
        return new ModelResponse(text, rawPayload, usage != null && usage.hasAnyValue() ? usage : null, stopReason);
    }

    @Override
    public String providerName() {
        return "openai";
    }

    private static String normalizeBaseUrl(String value) {
        String base = value == null || value.isBlank() ? ModelClientConfig.DEFAULT_OPENAI_BASE_URL : value.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.endsWith("/v1")) {
            base += "/v1";
        }
        return base;
    }

    private String extractText(ResponsesResponse response) {
        if (response.outputText() != null && !response.outputText().isBlank()) {
            return response.outputText();
        }
        if (response.output() != null) {
            for (OutputItem item : response.output()) {
                if (item == null || item.content() == null) {
                    continue;
                }
                for (OutputContentItem content : item.content()) {
                    if (content != null && content.text() != null && !content.text().isBlank()) {
                        return content.text();
                    }
                }
            }
        }
        if (response.choices() != null) {
            for (Choice choice : response.choices()) {
                if (choice == null || choice.message() == null) {
                    continue;
                }
                JsonNode content = choice.message().content();
                if (content == null || content.isNull()) {
                    continue;
                }
                if (content.isTextual()) {
                    return content.asText("");
                }
                if (content.isArray()) {
                    for (JsonNode node : content) {
                        JsonNode text = node.get("text");
                        if (text != null && !text.isNull()) {
                            return text.asText("");
                        }
                    }
                }
            }
        }
        return "";
    }

    private ModelUsage extractUsage(ResponsesResponse response) {
        JsonNode usageNode = response.usage();
        if (usageNode == null || usageNode.isNull()) {
            return null;
        }
        Integer inputTokens = integerField(usageNode, "input_tokens", "prompt_tokens");
        Integer outputTokens = integerField(usageNode, "output_tokens", "completion_tokens");
        Integer totalTokens = integerField(usageNode, "total_tokens");
        return new ModelUsage(inputTokens, outputTokens, totalTokens);
    }

    private String firstChoiceStopReason(ResponsesResponse response) {
        if (response.choices() == null) {
            return null;
        }
        for (Choice choice : response.choices()) {
            if (choice != null && choice.finishReason() != null && !choice.finishReason().isBlank()) {
                return choice.finishReason();
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Integer integerField(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToInt()) {
                return value.asInt();
            }
        }
        return null;
    }

    private record ResponsesRequest(
            String model,
            List<InputItem> input,
            @JsonProperty("max_output_tokens") int maxOutputTokens,
            double temperature
    ) {
        private static ResponsesRequest from(String model, String prompt, int maxNewTokens, double temperature) {
            return new ResponsesRequest(
                    model,
                    List.of(new InputItem("user", List.of(new InputContent("input_text", prompt)))),
                    maxNewTokens,
                    temperature
            );
        }
    }

    private record InputItem(
            String role,
            List<InputContent> content
    ) {}

    private record InputContent(
            String type,
            String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponsesResponse(
            @JsonAlias("output_text") String outputText,
            List<OutputItem> output,
            List<Choice> choices,
            JsonNode usage,
            @JsonProperty("stop_reason") String stopReason,
            @JsonProperty("finish_reason") String finishReason,
            String status,
            @JsonProperty("incomplete_details") IncompleteDetails incompleteDetails
    ) {
        private ResponsesResponse {
            if ((stopReason == null || stopReason.isBlank()) && finishReason != null && !finishReason.isBlank()) {
                stopReason = finishReason;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OutputItem(
            String type,
            List<OutputContentItem> content
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OutputContentItem(
            String type,
            String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(
            Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(
            JsonNode content
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IncompleteDetails(
            String reason
    ) {}
}
