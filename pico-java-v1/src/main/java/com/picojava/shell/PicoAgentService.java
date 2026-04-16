package com.picojava.shell;

import com.picojava.agent.ApprovalPolicy;
import com.picojava.agent.Pico;
import com.picojava.config.AgentProperties;
import com.picojava.config.ModelProperties;
import com.picojava.config.SessionProperties;
import com.picojava.model.ModelClient;
import com.picojava.model.ModelClientConfig;
import com.picojava.model.ModelClientFactory;
import com.picojava.session.SessionStore;
import com.picojava.workspace.WorkspaceContext;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class PicoAgentService {
    private static final Set<String> DEFAULT_SECRET_ENV_NAMES = Set.of(
            "OPENAI_API_KEY", "OPENAI_API_TOKEN", "ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN",
            "RIGHT_CODES_API_KEY", "GITHUB_PAT", "GH_PAT");

    private final AgentProperties agentProperties;
    private final ModelProperties modelProperties;
    private final SessionProperties sessionProperties;
    private final ShellSessionState shellSessionState;

    public PicoAgentService(AgentProperties agentProperties,
                            ModelProperties modelProperties,
                            SessionProperties sessionProperties,
                            ShellSessionState shellSessionState) {
        this.agentProperties = agentProperties;
        this.modelProperties = modelProperties;
        this.sessionProperties = sessionProperties;
        this.shellSessionState = shellSessionState;
    }

    public synchronized String ask(AgentExecutionRequest request) throws Exception {
        Pico pico = resolvePicoForAsk(request);
        return pico.ask(request.getPrompt());
    }

    public synchronized String useSession(String resume, String cwd) throws Exception {
        AgentExecutionRequest request = AgentExecutionRequest.builder()
                .resume(resume)
                .cwd(cwd)
                .build();
        shellSessionState.setPico(createPico(request, true));
        return sessionStatus();
    }

    public synchronized String sessionStatus() {
        if (!shellSessionState.hasActivePico()) {
            return "No active shell session. Use 'ask \"...\"' to start one or 'session-use latest'.";
        }
        Pico pico = shellSessionState.getPico();
        return "session=" + pico.session().getId() + "\n"
                + "cwd=" + pico.workspace().cwd() + "\n"
                + "repo_root=" + pico.workspace().repoRoot() + "\n"
                + "provider=" + pico.modelClient().providerName() + "\n"
                + "model=" + pico.modelClient().modelName() + "\n"
                + "approval=" + pico.approvalPolicy().name().toLowerCase();
    }

    public synchronized String clearSession() {
        shellSessionState.clear();
        return "Active shell session cleared.";
    }

    private Pico resolvePicoForAsk(AgentExecutionRequest request) throws Exception {
        if (shouldReuseCurrentSession(request)) {
            return shellSessionState.getPico();
        }
        Pico pico = createPico(request, hasText(resolveResume(request)));
        shellSessionState.setPico(pico);
        return pico;
    }

    private boolean shouldReuseCurrentSession(AgentExecutionRequest request) {
        return shellSessionState.hasActivePico() && !hasContextOverride(request);
    }

    private boolean hasContextOverride(AgentExecutionRequest request) {
        return hasText(request.getResume())
                || hasText(request.getCwd())
                || hasText(request.getProvider())
                || hasText(request.getModel())
                || hasText(request.getBaseUrl())
                || hasText(request.getHost())
                || hasText(request.getApiKey())
                || request.getTimeoutSeconds() != null
                || request.getTemperature() != null
                || request.getTopP() != null
                || hasText(request.getApproval())
                || request.getMaxSteps() != null
                || request.getMaxNewTokens() != null;
    }

    private Pico createPico(AgentExecutionRequest request, boolean restoreSession) throws Exception {
        String cwd = resolveCwd(request);
        WorkspaceContext workspace = WorkspaceContext.build(cwd);
        SessionStore sessionStore = new SessionStore(resolveSessionRoot(workspace));
        ModelClient modelClient = buildModelClient(request);
        ApprovalPolicy approvalPolicy = ApprovalPolicy.from(resolveApproval(request));
        int maxSteps = resolveMaxSteps(request);
        int maxNewTokens = resolveMaxNewTokens(request);
        Set<String> secretEnvNames = resolveSecretEnvNames();

        if (restoreSession) {
            String sessionId = resolveSessionId(sessionStore, resolveResume(request));
            return Pico.fromSession(
                    modelClient,
                    workspace,
                    sessionStore,
                    sessionId,
                    approvalPolicy,
                    maxSteps,
                    maxNewTokens,
                    secretEnvNames
            );
        }

        return new Pico(modelClient, workspace, sessionStore, approvalPolicy, maxSteps, maxNewTokens, secretEnvNames);
    }

    private ModelClient buildModelClient(AgentExecutionRequest request) {
        ModelClientConfig config = ModelClientConfig.resolve(
                resolveProvider(request),
                firstText(request.getModel(), modelProperties.getModel()),
                firstText(request.getBaseUrl(), modelProperties.getBaseUrl()),
                firstText(request.getHost(), modelProperties.getHost()),
                firstText(request.getApiKey(), modelProperties.getApiKey()),
                resolveTimeoutSeconds(request),
                request.getTemperature() != null ? request.getTemperature() : modelProperties.getTemperature(),
                request.getTopP() != null ? request.getTopP() : modelProperties.getTopP(),
                System.getenv()
        );
        return ModelClientFactory.create(config);
    }

    private Path resolveSessionRoot(WorkspaceContext workspace) {
        return workspace.repoRoot().resolve(sessionProperties.getDirectory());
    }

    private String resolveSessionId(SessionStore sessionStore, String resume) throws Exception {
        if ("latest".equalsIgnoreCase(resume)) {
            String latest = sessionStore.latest();
            if (!hasText(latest)) {
                throw new IllegalArgumentException("No saved session exists under " + sessionStore.root());
            }
            return latest;
        }
        return resume;
    }

    private String resolveCwd(AgentExecutionRequest request) {
        return firstText(request.getCwd(), agentProperties.getCwd());
    }

    private String resolveProvider(AgentExecutionRequest request) {
        return firstText(request.getProvider(), modelProperties.getProvider());
    }

    private String resolveApproval(AgentExecutionRequest request) {
        return firstText(request.getApproval(), agentProperties.getApproval());
    }

    private String resolveResume(AgentExecutionRequest request) {
        return firstText(request.getResume(), sessionProperties.getResume());
    }

    private int resolveMaxSteps(AgentExecutionRequest request) {
        return request.getMaxSteps() != null ? request.getMaxSteps() : agentProperties.getMaxSteps();
    }

    private int resolveMaxNewTokens(AgentExecutionRequest request) {
        return request.getMaxNewTokens() != null ? request.getMaxNewTokens() : agentProperties.getMaxNewTokens();
    }

    private Integer resolveTimeoutSeconds(AgentExecutionRequest request) {
        if (request.getTimeoutSeconds() != null) {
            return request.getTimeoutSeconds();
        }
        return Math.toIntExact(modelProperties.getTimeout().getSeconds());
    }

    private Set<String> resolveSecretEnvNames() {
        Set<String> names = new LinkedHashSet<>(DEFAULT_SECRET_ENV_NAMES);
        for (String name : agentProperties.getSecretEnvNames()) {
            if (hasText(name)) {
                names.add(name.trim().toUpperCase());
            }
        }
        String extraNames = System.getenv().getOrDefault("PICO_SECRET_ENV_NAMES", "");
        if (hasText(extraNames)) {
            for (String part : extraNames.split(",")) {
                if (hasText(part)) {
                    names.add(part.trim().toUpperCase());
                }
            }
        }
        return names;
    }

    private String firstText(String first, String fallback) {
        return hasText(first) ? first.trim() : (hasText(fallback) ? fallback.trim() : null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
