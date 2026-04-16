package com.picojava.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "pico.agent")
public class AgentProperties {
    @NotBlank
    private String cwd = ".";

    @Pattern(regexp = "(?i)ask|auto|never")
    private String approval = "ask";

    @Min(1)
    private int maxSteps = 8;

    @Min(1)
    private int maxNewTokens = 1800;

    private List<String> secretEnvNames = new ArrayList<>();
}
