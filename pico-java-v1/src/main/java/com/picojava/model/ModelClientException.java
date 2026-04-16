package com.picojava.model;

import com.picojava.common.TextUtils;

import java.io.IOException;

public class ModelClientException extends IOException {
    private final String provider;
    private final Integer statusCode;
    private final boolean retryable;
    private final String responseBody;

    public ModelClientException(String message, String provider, Integer statusCode, boolean retryable, String responseBody) {
        super(message);
        this.provider = provider == null ? "" : provider;
        this.statusCode = statusCode;
        this.retryable = retryable;
        this.responseBody = TextUtils.clip(responseBody == null ? "" : responseBody, 4000);
    }

    public ModelClientException(String message, String provider, Integer statusCode,
                                boolean retryable, String responseBody, Throwable cause) {
        super(message, cause);
        this.provider = provider == null ? "" : provider;
        this.statusCode = statusCode;
        this.retryable = retryable;
        this.responseBody = TextUtils.clip(responseBody == null ? "" : responseBody, 4000);
    }

    public String getProvider() {
        return provider;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
