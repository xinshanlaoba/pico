package com.picojava.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.picojava.common.JsonUtils;
import com.picojava.model.ModelClient;
import com.picojava.memory.LayeredMemory;
import com.picojava.run.RunStore;
import com.picojava.run.TaskState;
import com.picojava.session.MessageEntry;
import com.picojava.session.SessionState;
import com.picojava.session.SessionStore;
import com.picojava.tool.Tool;
import com.picojava.tool.ToolRegistry;
import com.picojava.workspace.WorkspaceContext;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class Pico {
    private static final int DEFAULT_MAX_DELEGATE_DEPTH = 1;

    private final ModelClient modelClient;
    private final WorkspaceContext workspace;
    private final SessionStore sessionStore;
    private final SessionState session;
    private final ApprovalPolicy approvalPolicy;
    private final int maxSteps;
    private final int maxNewTokens;
    private final int delegateDepth;
    private final int maxDelegateDepth;
    private final String parentRunId;
    private final Set<String> secretEnvNames;
    private final ToolRegistry toolRegistry;
    private final RunStore runStore;
    private final LayeredMemory memory;
    private final ContextManager contextManager;
    private TaskState currentTaskState;

    public Pico(ModelClient modelClient, WorkspaceContext workspace, SessionStore sessionStore,
                ApprovalPolicy approvalPolicy, int maxSteps, int maxNewTokens, Set<String> secretEnvNames) {
        this(modelClient, workspace, sessionStore, sessionStore.create(workspace.repoRoot().toString()),
                approvalPolicy, maxSteps, maxNewTokens, secretEnvNames, 0, DEFAULT_MAX_DELEGATE_DEPTH, "");
    }

    public Pico(ModelClient modelClient, WorkspaceContext workspace, SessionStore sessionStore, SessionState session,
                ApprovalPolicy approvalPolicy, int maxSteps, int maxNewTokens, Set<String> secretEnvNames) {
        this(modelClient, workspace, sessionStore, session, approvalPolicy, maxSteps, maxNewTokens,
                secretEnvNames, 0, DEFAULT_MAX_DELEGATE_DEPTH, "");
    }

    private Pico(ModelClient modelClient, WorkspaceContext workspace, SessionStore sessionStore, SessionState session,
                 ApprovalPolicy approvalPolicy, int maxSteps, int maxNewTokens, Set<String> secretEnvNames,
                 int delegateDepth, int maxDelegateDepth, String parentRunId) {
        this.modelClient = modelClient;
        this.workspace = workspace;
        this.sessionStore = sessionStore;
        this.session = session;
        this.approvalPolicy = approvalPolicy;
        this.maxSteps = maxSteps;
        this.maxNewTokens = maxNewTokens;
        this.delegateDepth = Math.max(delegateDepth, 0);
        this.maxDelegateDepth = Math.max(maxDelegateDepth, DEFAULT_MAX_DELEGATE_DEPTH);
        this.parentRunId = parentRunId == null ? "" : parentRunId;
        this.secretEnvNames = new HashSet<>(secretEnvNames);
        this.toolRegistry = new ToolRegistry();
        this.runStore = new RunStore(workspace.repoRoot().resolve(".pico/runs"));
        this.session.normalize();
        this.memory = new LayeredMemory(workspace.repoRoot(), session.getMemoryState(), session.getDistilledMemory());
        this.contextManager = new ContextManager(this);
        this.session.setWorkspaceRoot(this.session.getWorkspaceRoot().isBlank() ? workspace.repoRoot().toString() : this.session.getWorkspaceRoot());
        this.session.updateRuntimeConfig(
                approvalPolicy.name().toLowerCase(),
                maxSteps,
                maxNewTokens,
                this.secretEnvNames,
                modelClient.providerName(),
                modelClient.modelName()
        );
        this.memory.syncToSession(this.session);
        try {
            saveSession();
        } catch (IOException e) {
            throw new IllegalStateException("初始化会话失败：" + session.getId(), e);
        }
    }

    public static Pico fromSession(ModelClient modelClient, WorkspaceContext workspace, SessionStore sessionStore,
                                   String sessionId, ApprovalPolicy approvalPolicy, int maxSteps, int maxNewTokens,
                                   Set<String> secretEnvNames) throws IOException {
        SessionState session = sessionStore.load(sessionId);
        SessionState.RuntimeConfig runtimeConfig = session.getRuntimeConfig();
        ApprovalPolicy restoredApprovalPolicy = runtimeConfig.getApprovalPolicy().isBlank()
                ? approvalPolicy
                : ApprovalPolicy.from(runtimeConfig.getApprovalPolicy());
        int restoredMaxSteps = runtimeConfig.getMaxSteps() > 0 ? runtimeConfig.getMaxSteps() : maxSteps;
        int restoredMaxNewTokens = runtimeConfig.getMaxNewTokens() > 0 ? runtimeConfig.getMaxNewTokens() : maxNewTokens;
        Set<String> restoredSecretEnvNames = runtimeConfig.getSecretEnvNames().isEmpty()
                ? secretEnvNames
                : new LinkedHashSet<>(runtimeConfig.getSecretEnvNames());
        return new Pico(modelClient, workspace, sessionStore, session,
                restoredApprovalPolicy, restoredMaxSteps, restoredMaxNewTokens, restoredSecretEnvNames);
    }

    public String ask(String userMessage) throws Exception {
        return new AgentRunner(this).run(userMessage);
    }

    public boolean canDelegate() {
        return delegateDepth < maxDelegateDepth;
    }

    boolean approveTool(ToolCall toolCall, boolean risky) {
        if (!risky) {
            return true;
        }
        return switch (approvalPolicy) {
            case AUTO -> true;
            case NEVER -> false;
            case ASK -> askForApproval(toolCall);
        };
    }

    void appendHistory(String role, String content) {
        session.getHistory().add(new MessageEntry(role, content));
    }

    void startTask(String userMessage) {
        memory.setTaskSummary(userMessage);
    }

    void attachTaskState(TaskState taskState) {
        currentTaskState = taskState;
    }

    void clearTaskState(TaskState taskState) {
        if (currentTaskState == taskState) {
            currentTaskState = null;
        }
    }

    void rememberToolResult(ToolExecutionResult toolExecutionResult) {
        memory.recordToolResult(toolExecutionResult);
    }

    public TaskState.ChildRun delegateTask(String task, int childMaxSteps) throws Exception {
        if (!canDelegate()) {
            throw new IllegalStateException("delegate 深度已超过上限");
        }
        if (currentTaskState == null) {
            throw new IllegalStateException("delegate 需要在活跃 run 中执行");
        }

        SessionState childSession = cloneChildSession();
        Pico child = new Pico(
                modelClient,
                workspace,
                sessionStore,
                childSession,
                ApprovalPolicy.NEVER,
                Math.max(1, childMaxSteps),
                maxNewTokens,
                secretEnvNames,
                delegateDepth + 1,
                maxDelegateDepth,
                currentTaskState.getRunId()
        );

        Exception failure = null;
        try {
            child.ask(task);
        } catch (Exception e) {
            failure = e;
        }

        TaskState.ChildRun childRun = buildChildRun(task, child, failure);
        currentTaskState.recordChildRun(childRun);
        runStore.linkChildRun(currentTaskState.getRunId(), childRun);
        runStore.writeTaskState(currentTaskState);
        return childRun;
    }

    public void clearContext() {
        session.getHistory().clear();
        memory.clear();
        memory.syncToSession(session);
    }

    public void saveSession() throws IOException {
        memory.mergeSessionState(session);
        memory.syncToSession(session);
        session.updateRuntimeConfig(
                approvalPolicy.name().toLowerCase(),
                maxSteps,
                maxNewTokens,
                secretEnvNames,
                modelClient.providerName(),
                modelClient.modelName()
        );
        sessionStore.save(session);
    }

    void recordCompletedRun(String runId, String taskId, String status, String stopReason,
                            String finalAnswer, String error, java.time.Instant startedAt, java.time.Instant endedAt) {
        session.recordRun(SessionState.RunInfo.fromTask(
                runId,
                taskId,
                startedAt,
                endedAt,
                status,
                stopReason,
                finalAnswer,
                error
        ));
    }

    private boolean askForApproval(ToolCall toolCall) {
        String message = "是否批准工具 " + toolCall.name() + " args=" + toolCall.args() + " ? [y/N]: ";
        Console console = System.console();
        String input;
        if (console != null) {
            input = console.readLine(message);
        } else {
            System.out.print(message);
            input = new Scanner(System.in).nextLine();
        }
        return input != null && input.trim().equalsIgnoreCase("y");
    }

    public Path path(String relativeOrAbsolute) {
        Path path = Path.of(relativeOrAbsolute);
        Path resolved = path.isAbsolute() ? path.normalize() : root().resolve(path).normalize();
        if (!resolved.startsWith(root())) throw new IllegalArgumentException("路径不能逃逸工作区");
        return resolved;
    }

    public Map<String, String> shellEnv() {
        Map<String, String> env = new HashMap<>(System.getenv());
        for (String secret : secretEnvNames) env.remove(secret);
        return env;
    }

    String parentRunId() { return parentRunId; }
    RunStore runStore() { return runStore; }
    public int maxSteps() { return maxSteps; }
    int maxNewTokens() { return maxNewTokens; }
    int delegateDepth() { return delegateDepth; }
    LayeredMemory memory() { return memory; }
    ContextManager contextManager() { return contextManager; }
    Collection<Tool> toolsForPrompt() {
        return toolRegistry.all().stream()
                .filter(tool -> canDelegate() || !"delegate".equals(tool.name()))
                .toList();
    }

    public Path root() { return workspace.repoRoot(); }
    public WorkspaceContext workspace() { return workspace; }
    public SessionState session() { return session; }
    public ApprovalPolicy approvalPolicy() { return approvalPolicy; }
    public ToolRegistry toolRegistry() { return toolRegistry; }
    public ModelClient modelClient() { return modelClient; }
    public String memoryText() { return memory.renderMemoryText(); }

    private SessionState cloneChildSession() {
        SessionState child = sessionStore.create(workspace.repoRoot().toString());
        child.setHistory(copyHistory());
        child.setDistilledMemory(session.getDistilledMemory());
        child.setMemoryState(copyMemoryState());
        child.normalize();
        return child;
    }

    private java.util.List<MessageEntry> copyHistory() {
        java.util.List<MessageEntry> copied = new ArrayList<>();
        for (MessageEntry entry : session.getHistory()) {
            if ("assistant_raw".equals(entry.getRole())) {
                continue;
            }
            copied.add(new MessageEntry(entry.getRole(), entry.getContent()));
        }
        return copied;
    }

    private Map<String, Object> copyMemoryState() {
        Map<String, Object> memoryState = session.getMemoryState() == null ? Map.of() : session.getMemoryState();
        return JsonUtils.MAPPER.convertValue(memoryState, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private TaskState.ChildRun buildChildRun(String task, Pico child, Exception failure) throws Exception {
        if (!child.session().getRecentRuns().isEmpty()) {
            SessionState.RunInfo runInfo = child.session().getRecentRuns().get(0);
            return TaskState.ChildRun.fromDelegate(
                    runInfo.getRunId(),
                    runInfo.getTaskId(),
                    task,
                    runInfo.getStartedAt(),
                    runInfo.getEndedAt(),
                    runInfo.getStatus(),
                    runInfo.getStopReason(),
                    runInfo.getFinalAnswer(),
                    runInfo.getError()
            );
        }
        if (failure != null) {
            throw failure;
        }
        return TaskState.ChildRun.fromDelegate(
                "",
                "",
                task,
                Instant.now(),
                Instant.now(),
                "failed",
                "delegate_failed",
                "",
                "子 run 未生成运行元数据"
        );
    }
}
