package com.picojava.model;

public final class ModelClientFactory {
    private ModelClientFactory() {}

    public static ModelClient create(ModelClientConfig config) {
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
            default -> new OpenAiCompatibleModelClient(
                    config.model(),
                    config.baseUrl(),
                    config.apiKey(),
                    config.temperature(),
                    config.timeout()
            );
        };
    }
}
