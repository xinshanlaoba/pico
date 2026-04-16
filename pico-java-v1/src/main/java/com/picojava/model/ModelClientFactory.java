package com.picojava.model;

import io.micrometer.observation.ObservationRegistry;

public final class ModelClientFactory {
    private ModelClientFactory() {}

    public static ModelClient create(ModelClientConfig config) {
        return create(config, ObservationRegistry.NOOP);
    }

    public static ModelClient create(ModelClientConfig config, ObservationRegistry observationRegistry) {
        return switch (config.provider()) {
            case "anthropic" -> new AnthropicCompatibleModelClient(
                    config.model(),
                    config.baseUrl(),
                    config.apiKey(),
                    config.temperature(),
                    config.timeout()
            );
            case "ollama" -> new OllamaModelClient(
                    config.model(),
                    config.baseUrl(),
                    config.temperature(),
                    config.topP(),
                    config.timeout()
            );
            default -> new SpringAiOpenAiModelClient(
                    config.model(),
                    config.baseUrl(),
                    config.apiKey(),
                    config.temperature(),
                    config.topP(),
                    config.timeout(),
                    config.maxRetries(),
                    observationRegistry
            );
        };
    }
}
