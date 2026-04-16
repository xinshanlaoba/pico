package com.picojava.shell;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class AgentExecutionRequest {
    private final String prompt;
    private final String resume;
    private final String cwd;
    private final String provider;
    private final String model;
    private final String baseUrl;
    private final String host;
    private final String apiKey;
    private final Integer timeoutSeconds;
    private final Double temperature;
    private final Double topP;
    private final String approval;
    private final Integer maxSteps;
    private final Integer maxNewTokens;
}
