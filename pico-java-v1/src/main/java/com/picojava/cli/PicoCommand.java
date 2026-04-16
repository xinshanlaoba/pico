package com.picojava.cli;

import com.picojava.agent.ApprovalPolicy;
import com.picojava.agent.Pico;
import com.picojava.model.ModelClient;
import com.picojava.model.ModelClientConfig;
import com.picojava.model.ModelClientFactory;
import com.picojava.model.ModelConfigurationException;
import com.picojava.session.SessionException;
import com.picojava.session.SessionStore;
import com.picojava.workspace.WorkspaceContext;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "pico", mixinStandardHelpOptions = true,
        description = "面向 Ollama、OpenAI 兼容接口或 Anthropic 兼容接口的极简本地编程 agent。")
public class PicoCommand implements Callable<Integer> {
    private static final Set<String> DEFAULT_SECRET_ENV_NAMES = Set.of(
            "OPENAI_API_KEY", "OPENAI_API_TOKEN", "ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN",
            "RIGHT_CODES_API_KEY", "GITHUB_PAT", "GH_PAT");

    @CommandLine.Parameters(arity = "0..*", description = "可选的一次性 prompt。")
    private List<String> prompt = new ArrayList<>();

    @CommandLine.Option(names = "--cwd", defaultValue = ".", description = "工作区目录。")
    private String cwd;

    @CommandLine.Option(names = "--provider", defaultValue = "openai", description = "模型后端：${COMPLETION-CANDIDATES}")
    private String provider;

    @CommandLine.Option(names = "--model", description = "覆盖默认模型名称。")
    private String model;

    @CommandLine.Option(names = "--host", description = "Ollama 服务地址。")
    private String host;

    @CommandLine.Option(names = "--base-url", description = "模型服务 API base URL。")
    private String baseUrl;

    @CommandLine.Option(names = "--api-key", description = "覆盖默认 API key。")
    private String apiKey;

    @CommandLine.Option(names = "--temperature", defaultValue = "0.2")
    private double temperature;

    @CommandLine.Option(names = "--top-p", defaultValue = "0.95")
    private double topP;

    @CommandLine.Option(names = "--timeout", description = "模型请求超时时间，单位秒。")
    private Integer timeoutSeconds;

    @CommandLine.Option(names = "--approval", defaultValue = "ask", description = "工具审批策略：ask/auto/never")
    private String approval;

    @CommandLine.Option(names = "--resume", description = "恢复指定 session id，或使用 latest 恢复最近一次 session。")
    private String resume;

    @CommandLine.Option(names = "--max-steps", defaultValue = "8")
    private int maxSteps;

    @CommandLine.Option(names = "--max-new-tokens", defaultValue = "1800")
    private int maxNewTokens;

    @CommandLine.Option(names = "--secret-env-name", description = "额外指定需要按敏感信息处理的环境变量名。")
    private List<String> secretEnvNames = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        Path workingDirectory = Path.of(cwd).toAbsolutePath().normalize();
        if (!Files.exists(workingDirectory) || !Files.isDirectory(workingDirectory)) {
            throw new CommandLine.ParameterException(new CommandLine(this), "目录不存在或不是目录：" + workingDirectory);
        }

        Pico pico = buildAgent();
        System.out.println(buildWelcome(pico));
        if (!prompt.isEmpty()) {
            String answer = pico.ask(String.join(" ", prompt));
            System.out.println(answer);
            return 0;
        }
        return new ReplRunner(pico).run();
    }

    private Pico buildAgent() throws Exception {
        Set<String> configuredSecretNames = new HashSet<>(DEFAULT_SECRET_ENV_NAMES);
        for (String name : secretEnvNames) configuredSecretNames.add(name.toUpperCase());
        String extraNames = System.getenv().getOrDefault("PICO_SECRET_ENV_NAMES", "");
        if (!extraNames.isBlank()) {
            for (String part : extraNames.split(",")) {
                if (!part.isBlank()) configuredSecretNames.add(part.trim().toUpperCase());
            }
        }
        WorkspaceContext workspace = WorkspaceContext.build(cwd);
        SessionStore store = new SessionStore(workspace.repoRoot().resolve(".pico/sessions"));
        ModelClient modelClient = buildModelClient();
        ApprovalPolicy policy = ApprovalPolicy.from(approval);
        if (resume != null && !resume.isBlank()) {
            String sessionId = "latest".equals(resume) ? store.latest() : resume;
            if (sessionId == null || sessionId.isBlank()) {
                throw new CommandLine.ParameterException(
                        new CommandLine(this),
                        "未找到已保存的 session：" + store.root()
                );
            }
            try {
                return Pico.fromSession(modelClient, workspace, store, sessionId, policy, maxSteps, maxNewTokens, configuredSecretNames);
            } catch (SessionException e) {
                throw new CommandLine.ParameterException(new CommandLine(this), e.getMessage(), e);
            }
        }
        return new Pico(modelClient, workspace, store, policy, maxSteps, maxNewTokens, configuredSecretNames);
    }

    private ModelClient buildModelClient() {
        try {
            ModelClientConfig config = ModelClientConfig.resolve(
                    provider,
                    model,
                    baseUrl,
                    host,
                    apiKey,
                    timeoutSeconds,
                    temperature,
                    topP,
                    environment()
            );
            return ModelClientFactory.create(config);
        } catch (ModelConfigurationException e) {
            throw new CommandLine.ParameterException(new CommandLine(this), e.getMessage(), e);
        }
    }

    Map<String, String> environment() {
        return System.getenv();
    }

    private String buildWelcome(Pico pico) {
        return "=".repeat(68) + "\n" +
                "pico-java  |  本地编程 agent\n" +
                "工作区：   " + pico.workspace().cwd() + "\n" +
                "分支：     " + pico.workspace().branch() + "\n" +
                "provider: " + pico.modelClient().providerName() + "\n" +
                "模型：     " + pico.modelClient().modelName() + "\n" +
                "审批策略： " + pico.approvalPolicy() + "\n" +
                "session:  " + pico.session().getId() + "\n" +
                "=".repeat(68);
    }
}
