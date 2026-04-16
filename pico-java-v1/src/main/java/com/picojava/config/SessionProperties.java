package com.picojava.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "pico.session")
public class SessionProperties {
    private String resume;

    @NotBlank
    private String directory = ".pico/sessions";
}
