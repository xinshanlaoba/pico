package com.picojava.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

class ModelClientConfigTest {
    @Test
    void resolvesConfigFromEnvironment() {
        ModelClientConfig config = ModelClientConfig.resolve(
                "openai",
                null,
                null,
                null,
                null,
                null,
                0.2,
                0.95,
                Map.of(
                        "OPENAI_MODEL", "gpt-env",
                        "OPENAI_BASE_URL", "https://env.openai.test",
                        "OPENAI_API_KEY", "sk-env",
                        "PICO_MODEL_TIMEOUT_SECONDS", "45"
                )
        );

        Assertions.assertEquals("openai", config.provider());
        Assertions.assertEquals("gpt-env", config.model());
        Assertions.assertEquals("https://env.openai.test", config.baseUrl());
        Assertions.assertEquals("sk-env", config.apiKey());
        Assertions.assertEquals(Duration.ofSeconds(45), config.timeout());
    }

    @Test
    void explicitOptionsOverrideEnvironment() {
        ModelClientConfig config = ModelClientConfig.resolve(
                "anthropic",
                "claude-cli",
                "https://cli.anthropic.test",
                null,
                "ak-cli",
                12,
                0.1,
                0.9,
                Map.of(
                        "ANTHROPIC_MODEL", "claude-env",
                        "ANTHROPIC_API_BASE", "https://env.anthropic.test",
                        "ANTHROPIC_API_KEY", "ak-env",
                        "ANTHROPIC_TIMEOUT_SECONDS", "99"
                )
        );

        Assertions.assertEquals("claude-cli", config.model());
        Assertions.assertEquals("https://cli.anthropic.test", config.baseUrl());
        Assertions.assertEquals("ak-cli", config.apiKey());
        Assertions.assertEquals(Duration.ofSeconds(12), config.timeout());
    }
}
