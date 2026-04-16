package com.picojava;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.picojava.config")
public class PicoApplication {
    public static void main(String[] args) {
        // Ignore unrelated host DEBUG environment flags unless the user passes --debug explicitly.
        System.setProperty("debug", "false");

        new SpringApplicationBuilder(PicoApplication.class)
                .bannerMode(Banner.Mode.OFF)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run(args);
    }
}
