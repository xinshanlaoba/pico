package com.picojava.model;

import com.picojava.common.TextUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

public abstract class AbstractHttpModelClient implements ModelClient {
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(408, 409, 425, 429, 500, 502, 503, 504);

    protected final HttpClient httpClient;
    protected final String model;
    protected final Duration timeout;
    protected final int maxRetries;
    private final HttpExecutor executor;

    protected AbstractHttpModelClient(String model, Duration timeout) {
        this(model, timeout, ModelClientConfig.DEFAULT_MAX_RETRIES, null);
    }

    protected AbstractHttpModelClient(String model, Duration timeout, int maxRetries) {
        this(model, timeout, maxRetries, null);
    }

    protected AbstractHttpModelClient(String model, Duration timeout, int maxRetries, HttpExecutor executor) {
        if (model == null || model.isBlank()) {
            throw new ModelConfigurationException("模型名称不能为空");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new ModelConfigurationException("超时时间必须为正数");
        }
        if (maxRetries < 0) {
            throw new ModelConfigurationException("maxRetries 必须大于等于 0");
        }
        this.model = model.trim();
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.executor = executor != null
                ? executor
                : request -> httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Override
    public final String complete(String prompt, int maxNewTokens) throws IOException, InterruptedException {
        return completeResponse(prompt, maxNewTokens).textContent();
    }

    @Override
    public final ModelResponse completeResponse(String prompt, int maxNewTokens) throws IOException, InterruptedException {
        return completeResponse(new ModelRequest(prompt, maxNewTokens));
    }

    @Override
    public String modelName() {
        return model;
    }

    protected abstract HttpRequest buildRequest(ModelRequest request) throws IOException;

    protected abstract ModelResponse parseResponse(String rawPayload) throws IOException;

    protected final HttpRequest.Builder jsonRequestBuilder(String uri) {
        return HttpRequest.newBuilder()
                .uri(java.net.URI.create(uri))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    protected ModelResponse completeResponse(ModelRequest request) throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<String> response = executor.send(buildRequest(request));
                String body = response.body() == null ? "" : response.body();
                if (response.statusCode() >= 400) {
                    boolean retryable = isRetryableStatus(response.statusCode());
                    ModelClientException failure = failureForStatus(response.statusCode(), body, retryable);
                    if (retryable && attempt < maxRetries) {
                        sleepBeforeRetry(attempt + 1);
                        lastFailure = failure;
                        continue;
                    }
                    throw failure;
                }
                try {
                    return parseResponse(body);
                } catch (IOException e) {
                    throw new ModelClientException(
                            providerName() + " 响应解析失败：" + e.getMessage(),
                            providerName(),
                            response.statusCode(),
                            false,
                            body,
                            e
                    );
                }
            } catch (HttpTimeoutException e) {
                ModelClientException failure = new ModelClientException(
                        providerName() + " request timed out after " + timeout.toSeconds() + "s",
                        providerName(),
                        null,
                        true,
                        "",
                        e
                );
                if (attempt < maxRetries) {
                    sleepBeforeRetry(attempt + 1);
                    lastFailure = failure;
                    continue;
                }
                throw failure;
            } catch (ModelClientException e) {
                throw e;
            } catch (IOException e) {
                boolean retryable = isRetryableIo(e);
                ModelClientException failure = new ModelClientException(
                        providerName() + " 请求失败：" + TextUtils.clip(e.getMessage(), 1000),
                        providerName(),
                        null,
                        retryable,
                        "",
                        e
                );
                if (retryable && attempt < maxRetries) {
                    sleepBeforeRetry(attempt + 1);
                    lastFailure = failure;
                    continue;
                }
                throw failure;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new ModelClientException(providerName() + " 请求失败", providerName(), null, false, "");
    }

    protected boolean isRetryableStatus(int statusCode) {
        return RETRYABLE_STATUS_CODES.contains(statusCode);
    }

    private ModelClientException failureForStatus(int statusCode, String body, boolean retryable) {
        return new ModelClientException(
                providerName() + " 请求失败：HTTP " + statusCode + " body=" + TextUtils.clip(body, 2000),
                providerName(),
                statusCode,
                retryable,
                body
        );
    }

    private boolean isRetryableIo(IOException exception) {
        return !(exception instanceof java.io.EOFException)
                && !(exception instanceof java.io.FileNotFoundException)
                && !(exception instanceof com.fasterxml.jackson.core.JsonProcessingException);
    }

    private void sleepBeforeRetry(int attempt) throws InterruptedException {
        Thread.sleep(Math.min(1000L, 200L * attempt));
    }
}
