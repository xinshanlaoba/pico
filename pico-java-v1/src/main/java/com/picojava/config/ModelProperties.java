package com.picojava.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "pico.model")
public class ModelProperties {
    @Pattern(regexp = "(?i)openai|anthropic|ollama")
    private String provider = "openai";

    private String model;

    private String baseUrl;

    private String apiKey;

    private String host;

    @NotNull
    private Duration timeout = Duration.ofSeconds(300);

    @DecimalMin("0.0")
    private double temperature = 0.2;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double topP = 0.95;
}
