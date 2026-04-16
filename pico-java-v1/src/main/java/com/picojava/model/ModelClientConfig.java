package com.picojava.model;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

public record ModelClientConfig(
        String provider,
        String model,
        String baseUrl,
        String apiKey,
        Duration timeout,
        double temperature,
        double topP,
        int maxRetries
) {
    public static final String DEFAULT_PROVIDER = "openai";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-5.4";
    public static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-6";
    public static final String DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com/v1";
    public static final String DEFAULT_OLLAMA_MODEL = "qwen3.5:4b";
    public static final String DEFAULT_OLLAMA_BASE_URL = "http://127.0.0.1:11434";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(300);
    public static final int DEFAULT_MAX_RETRIES = 2;

    public ModelClientConfig {
        provider = normalizeProvider(provider);
        model = requireText(model, "模型名称不能为空");
        baseUrl = requireText(baseUrl, "Base URL 不能为空");
        apiKey = apiKey == null ? "" : apiKey.trim();
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new ModelConfigurationException("超时时间必须为正数");
        }
        if (maxRetries < 0) {
            throw new ModelConfigurationException("maxRetries 必须大于等于 0");
        }
    }

    public static ModelClientConfig resolve(String provider, String modelOption, String baseUrlOption, String hostOption,
                                            String apiKeyOption, Integer timeoutSecondsOption,
                                            double temperature, double topP, Map<String, String> env) {
        Map<String, String> safeEnv = env == null ? Map.of() : env;
        String normalizedProvider = normalizeProvider(provider);
        String resolvedModel = resolveModel(normalizedProvider, modelOption, safeEnv);
        String resolvedBaseUrl = resolveBaseUrl(normalizedProvider, baseUrlOption, hostOption, safeEnv);
        String resolvedApiKey = resolveApiKey(normalizedProvider, apiKeyOption, safeEnv);
        Duration resolvedTimeout = Duration.ofSeconds(resolveTimeoutSeconds(normalizedProvider, timeoutSecondsOption, safeEnv));
        return new ModelClientConfig(
                normalizedProvider,
                resolvedModel,
                resolvedBaseUrl,
                resolvedApiKey,
                resolvedTimeout,
                temperature,
                topP,
                DEFAULT_MAX_RETRIES
        );
    }

    private static String resolveModel(String provider, String option, Map<String, String> env) {
        if (option != null && !option.isBlank()) {
            return option.trim();
        }
        return switch (provider) {
            case "anthropic" -> firstValue(env, "ANTHROPIC_MODEL", DEFAULT_ANTHROPIC_MODEL);
            case "ollama" -> firstValue(env, "OLLAMA_MODEL", DEFAULT_OLLAMA_MODEL);
            default -> firstValue(env, "OPENAI_MODEL", DEFAULT_OPENAI_MODEL);
        };
    }

    private static String resolveBaseUrl(String provider, String baseUrlOption, String hostOption, Map<String, String> env) {
        String explicit = provider.equals("ollama") && (baseUrlOption == null || baseUrlOption.isBlank())
                ? hostOption
                : baseUrlOption;
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        return switch (provider) {
            case "anthropic" -> firstValue(env, "ANTHROPIC_API_BASE", "ANTHROPIC_BASE_URL", "PICO_BASE_URL", DEFAULT_ANTHROPIC_BASE_URL);
            case "ollama" -> firstValue(env, "OLLAMA_HOST", "OLLAMA_BASE_URL", "PICO_BASE_URL", DEFAULT_OLLAMA_BASE_URL);
            default -> firstValue(env, "OPENAI_API_BASE", "OPENAI_BASE_URL", "PICO_BASE_URL", DEFAULT_OPENAI_BASE_URL);
        };
    }

    private static String resolveApiKey(String provider, String option, Map<String, String> env) {
        if (option != null && !option.isBlank()) {
            return option.trim();
        }
        return switch (provider) {
            case "anthropic" -> firstValue(env, "ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN", "RIGHT_CODES_API_KEY", "");
            case "ollama" -> "";
            default -> firstValue(env, "OPENAI_API_KEY", "OPENAI_API_TOKEN", "");
        };
    }

    private static long resolveTimeoutSeconds(String provider, Integer option, Map<String, String> env) {
        if (option != null) {
            if (option <= 0) {
                throw new ModelConfigurationException("--timeout 必须为正数");
            }
            return option;
        }
        String providerKey = switch (provider) {
            case "anthropic" -> "ANTHROPIC_TIMEOUT_SECONDS";
            case "ollama" -> "OLLAMA_TIMEOUT_SECONDS";
            default -> "OPENAI_TIMEOUT_SECONDS";
        };
        String raw = firstNonBlank(env, providerKey, "PICO_MODEL_TIMEOUT_SECONDS");
        if (raw == null) {
            return DEFAULT_TIMEOUT.toSeconds();
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new ModelConfigurationException("环境变量中的 timeout 值无效：" + raw);
        }
    }

    private static String normalizeProvider(String provider) {
        String value = provider == null || provider.isBlank() ? DEFAULT_PROVIDER : provider.trim().toLowerCase(Locale.ROOT);
        if (!value.equals("openai") && !value.equals("anthropic") && !value.equals("ollama")) {
            throw new ModelConfigurationException("不支持的 provider：" + provider);
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ModelConfigurationException(message);
        }
        return value.trim();
    }

    private static String firstValue(Map<String, String> env, String firstName, String secondName, String thirdName, String fallback) {
        String value = firstNonBlank(env, firstName, secondName, thirdName);
        return value == null ? fallback : value;
    }

    private static String firstValue(Map<String, String> env, String firstName, String secondName, String fallback) {
        String value = firstNonBlank(env, firstName, secondName);
        return value == null ? fallback : value;
    }

    private static String firstValue(Map<String, String> env, String firstName, String fallback) {
        String value = firstNonBlank(env, firstName);
        return value == null ? fallback : value;
    }

    private static String firstNonBlank(Map<String, String> env, String... names) {
        for (String name : names) {
            String value = env.get(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
