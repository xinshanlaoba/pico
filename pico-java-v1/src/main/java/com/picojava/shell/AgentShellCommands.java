package com.picojava.shell;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class AgentShellCommands {
    private final PicoAgentService picoAgentService;

    public AgentShellCommands(PicoAgentService picoAgentService) {
        this.picoAgentService = picoAgentService;
    }

    @ShellMethod(key = "ask", value = "Run one agent turn. In interactive shell, repeated calls reuse the current in-memory session.")
    public String ask(
            String prompt,
            @ShellOption(value = "--resume", defaultValue = ShellOption.NULL) String resume,
            @ShellOption(value = "--cwd", defaultValue = ShellOption.NULL) String cwd,
            @ShellOption(value = "--provider", defaultValue = ShellOption.NULL) String provider,
            @ShellOption(value = "--model", defaultValue = ShellOption.NULL) String model,
            @ShellOption(value = "--base-url", defaultValue = ShellOption.NULL) String baseUrl,
            @ShellOption(value = "--host", defaultValue = ShellOption.NULL) String host,
            @ShellOption(value = "--api-key", defaultValue = ShellOption.NULL) String apiKey,
            @ShellOption(value = "--timeout", defaultValue = ShellOption.NULL) Integer timeoutSeconds,
            @ShellOption(value = "--temperature", defaultValue = ShellOption.NULL) Double temperature,
            @ShellOption(value = "--top-p", defaultValue = ShellOption.NULL) Double topP,
            @ShellOption(value = "--approval", defaultValue = ShellOption.NULL) String approval,
            @ShellOption(value = "--max-steps", defaultValue = ShellOption.NULL) Integer maxSteps,
            @ShellOption(value = "--max-new-tokens", defaultValue = ShellOption.NULL) Integer maxNewTokens
    ) {
        try {
            return picoAgentService.ask(AgentExecutionRequest.builder()
                    .prompt(prompt)
                    .resume(resume)
                    .cwd(cwd)
                    .provider(provider)
                    .model(model)
                    .baseUrl(baseUrl)
                    .host(host)
                    .apiKey(apiKey)
                    .timeoutSeconds(timeoutSeconds)
                    .temperature(temperature)
                    .topP(topP)
                    .approval(approval)
                    .maxSteps(maxSteps)
                    .maxNewTokens(maxNewTokens)
                    .build());
        } catch (Exception e) {
            return formatError(e);
        }
    }

    @ShellMethod(key = "session-use", value = "Resume an existing session into the current interactive shell context.")
    public String sessionUse(
            String resume,
            @ShellOption(value = "--cwd", defaultValue = ShellOption.NULL) String cwd
    ) {
        try {
            return picoAgentService.useSession(resume, cwd);
        } catch (Exception e) {
            return formatError(e);
        }
    }

    @ShellMethod(key = "session-status", value = "Show the current interactive shell session.")
    public String sessionStatus() {
        return picoAgentService.sessionStatus();
    }

    @ShellMethod(key = "session-clear", value = "Clear the current interactive shell session.")
    public String sessionClear() {
        return picoAgentService.clearSession();
    }

    private String formatError(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getMessage();
        }
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return "ERROR: " + message;
    }
}
