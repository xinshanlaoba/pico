package com.picojava.model;

import com.picojava.common.JsonUtils;
import com.picojava.common.TextUtils;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SpringAiOpenAiModelClient implements ModelClient {
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(408, 409, 425, 429, 500, 502, 503, 504);

    private final String model;
    private final String baseUrl;
    private final double temperature;
    private final double topP;
    private final ChatClient chatClient;

    public SpringAiOpenAiModelClient(String model, String baseUrl, String apiKey,
                                     double temperature, double topP, Duration timeout,
                                     int maxRetries, ObservationRegistry observationRegistry) {
        this.model = requireText(model, "Model name cannot be blank");
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.temperature = temperature;
        this.topP = topP;

        ObservationRegistry registry = observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
        OpenAiApi openAiApi = buildOpenAiApi(apiKey, timeout);
        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(this.model)
                .temperature(temperature)
                .topP(topP)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(defaultOptions)
                .retryTemplate(buildRetryTemplate(maxRetries))
                .observationRegistry(registry)
                .build();
        this.chatClient = ChatClient.create(chatModel, registry);
    }

    @Override
    public String complete(String prompt, int maxNewTokens) throws IOException, InterruptedException {
        return completeResponse(prompt, maxNewTokens).textContent();
    }

    @Override
    public ModelResponse completeResponse(ModelRequest request) throws IOException {
        try {
            OpenAiChatOptions options = buildOptions(request);
            Prompt prompt = new Prompt(new UserMessage(request.prompt()), options);
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
            return toModelResponse(response);
        } catch (RestClientResponseException e) {
            throw new ModelClientException(
                    "openai request failed: HTTP " + e.getStatusCode().value() + " body=" + TextUtils.clip(e.getResponseBodyAsString(), 2000),
                    providerName(),
                    e.getStatusCode().value(),
                    isRetryableStatus(e.getStatusCode().value()),
                    e.getResponseBodyAsString(),
                    e
            );
        } catch (ResourceAccessException e) {
            throw new ModelClientException(
                    "openai request failed: " + TextUtils.clip(e.getMessage(), 1000),
                    providerName(),
                    null,
                    true,
                    "",
                    e
            );
        } catch (ModelClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ModelClientException(
                    "openai request failed: " + TextUtils.clip(e.getMessage(), 1000),
                    providerName(),
                    null,
                    false,
                    "",
                    e
            );
        }
    }

    @Override
    public boolean supportsNativeToolCalling() {
        return true;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public String modelName() {
        return model;
    }

    private OpenAiApi buildOpenAiApi(String apiKey, Duration timeout) {
        Duration safeTimeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? ModelClientConfig.DEFAULT_TIMEOUT
                : timeout;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.toIntExact(Math.min(Integer.MAX_VALUE, safeTimeout.toMillis()));
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);

        OpenAiApi.Builder builder = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .completionsPath("/chat/completions")
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.apiKey(apiKey.trim());
        }
        return builder.build();
    }

    private RetryTemplate buildRetryTemplate(int maxRetries) {
        int maxAttempts = Math.max(1, maxRetries + 1);
        return RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .fixedBackoff(200)
                .retryOn(RestClientResponseException.class)
                .retryOn(ResourceAccessException.class)
                .build();
    }

    private OpenAiChatOptions buildOptions(ModelRequest request) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(request.maxNewTokens())
                .parallelToolCalls(false);

        if (!request.toolDefinitions().isEmpty()) {
            builder.tools(toFunctionTools(request.toolDefinitions()))
                    .toolChoice("auto")
                    .internalToolExecutionEnabled(false);
        }

        return builder.build();
    }

    private List<OpenAiApi.FunctionTool> toFunctionTools(List<ModelToolDefinition> toolDefinitions) {
        return toolDefinitions.stream()
                .map(tool -> new OpenAiApi.FunctionTool(
                        new OpenAiApi.FunctionTool.Function(tool.name(), tool.description(), tool.jsonSchema())
                ))
                .toList();
    }

    private ModelResponse toModelResponse(ChatResponse response) throws IOException {
        AssistantMessage assistantMessage = response == null || response.getResult() == null
                ? new AssistantMessage("")
                : response.getResult().getOutput();
        String content = toLegacyContent(assistantMessage);
        String stopReason = finishReason(response);
        ModelUsage usage = toUsage(response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage());
        String rawPayload = JsonUtils.MAPPER.writeValueAsString(buildRawPayload(response, assistantMessage, stopReason, usage));
        return new ModelResponse(content, rawPayload, usage != null && usage.hasAnyValue() ? usage : null, stopReason);
    }

    private Map<String, Object> buildRawPayload(ChatResponse response, AssistantMessage assistantMessage,
                                                String stopReason, ModelUsage usage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", providerName());
        payload.put("model", response == null || response.getMetadata() == null || response.getMetadata().getModel() == null
                ? model
                : response.getMetadata().getModel());
        payload.put("text", assistantMessage == null ? "" : assistantMessage.getText());
        payload.put("tool_calls", assistantMessage == null ? List.of() : assistantMessage.getToolCalls());
        payload.put("stop_reason", stopReason);
        if (usage != null && usage.hasAnyValue()) {
            Map<String, Object> usagePayload = new LinkedHashMap<>();
            usagePayload.put("input_tokens", usage.inputTokens());
            usagePayload.put("output_tokens", usage.outputTokens());
            usagePayload.put("total_tokens", usage.totalTokens());
            payload.put("usage", usagePayload);
        }
        return payload;
    }

    private String toLegacyContent(AssistantMessage assistantMessage) throws IOException {
        if (assistantMessage == null) {
            return "";
        }
        if (assistantMessage.hasToolCalls()) {
            AssistantMessage.ToolCall toolCall = assistantMessage.getToolCalls().get(0);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", toolCall.name());
            payload.put("args", parseArguments(toolCall.arguments()));
            return "<tool>" + JsonUtils.MAPPER.writeValueAsString(payload) + "</tool>";
        }
        return assistantMessage.getText() == null ? "" : assistantMessage.getText();
    }

    private Map<String, Object> parseArguments(String arguments) throws IOException {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        return JsonUtils.MAPPER.readValue(arguments, JsonUtils.MAPPER.getTypeFactory()
                .constructMapType(LinkedHashMap.class, String.class, Object.class));
    }

    private String finishReason(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getMetadata() == null) {
            return "";
        }
        ChatGenerationMetadata metadata = response.getResult().getMetadata();
        return metadata.getFinishReason() == null ? "" : metadata.getFinishReason().toLowerCase(Locale.ROOT);
    }

    private ModelUsage toUsage(Usage usage) {
        if (usage == null) {
            return null;
        }
        return new ModelUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    private boolean isRetryableStatus(int statusCode) {
        return RETRYABLE_STATUS_CODES.contains(statusCode);
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

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ModelConfigurationException(message);
        }
        return value.trim();
    }
}
