package com.picojava.config;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.jline.PromptProvider;

@Configuration
public class ShellConfiguration {
    @Bean
    PromptProvider promptProvider(ShellProperties shellProperties) {
        return () -> new AttributedString(
                shellProperties.getPrompt() + " ",
                AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)
        );
    }
}
