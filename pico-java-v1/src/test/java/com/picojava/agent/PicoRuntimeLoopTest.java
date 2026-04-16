package com.picojava.agent;

import com.picojava.model.ModelClient;
import com.picojava.session.SessionStore;
import com.picojava.workspace.WorkspaceContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

class PicoRuntimeLoopTest {
    @TempDir
    Path tempDir;

    @Test
    void feedsToolResultBackIntoNextPrompt() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "hello world\n", StandardCharsets.UTF_8);
        StubModelClient modelClient = new StubModelClient(
                "<tool>{\"name\":\"read_file\",\"args\":{\"path\":\"README.md\",\"start\":1,\"end\":5}}</tool>",
                "<final>done</final>"
        );
        Pico pico = newPico(modelClient, ApprovalPolicy.AUTO, 4);

        String answer = pico.ask("inspect the readme");

        Assertions.assertEquals("done", answer);
        Assertions.assertEquals(2, modelClient.prompts().size());
        Assertions.assertTrue(modelClient.prompts().get(1).contains("read_file ->"));
        Assertions.assertTrue(modelClient.prompts().get(1).contains("hello world"));
    }

    @Test
    void retriesOnMalformedToolPayload() throws Exception {
        StubModelClient modelClient = new StubModelClient(
                "<tool>{bad json}</tool>",
                "<final>done</final>"
        );
        Pico pico = newPico(modelClient, ApprovalPolicy.AUTO, 4);

        String answer = pico.ask("do it");

        Assertions.assertEquals("done", answer);
        Assertions.assertTrue(modelClient.prompts().get(1).contains("运行时提示：模型返回的工具 JSON 格式不正确"));
    }

    @Test
    void continuesAfterToolValidationFailure() throws Exception {
        StubModelClient modelClient = new StubModelClient(
                "<tool>{\"name\":\"read_file\",\"args\":{\"path\":\"missing.txt\"}}</tool>",
                "<final>done</final>"
        );
        Pico pico = newPico(modelClient, ApprovalPolicy.AUTO, 4);

        String answer = pico.ask("inspect missing file");

        Assertions.assertEquals("done", answer);
        Assertions.assertTrue(modelClient.prompts().get(1).contains("工具 read_file 的参数无效"));
    }

    @Test
    void retriesOnEmptyModelResponse() throws Exception {
        StubModelClient modelClient = new StubModelClient(
                "",
                "<final>done</final>"
        );
        Pico pico = newPico(modelClient, ApprovalPolicy.AUTO, 4);

        String answer = pico.ask("say something");

        Assertions.assertEquals("done", answer);
        Assertions.assertTrue(modelClient.prompts().get(1).contains("运行时提示：模型返回了空响应"));
    }

    @Test
    void stopsAtStepLimit() throws Exception {
        StubModelClient modelClient = new StubModelClient(
                "<tool>{\"name\":\"list_files\",\"args\":{\"path\":\".\"}}</tool>",
                "<tool>{\"name\":\"list_files\",\"args\":{\"path\":\".\"}}</tool>"
        );
        Pico pico = newPico(modelClient, ApprovalPolicy.AUTO, 2);

        String answer = pico.ask("loop forever");

        Assertions.assertEquals("已达到步骤上限，尚未生成最终答案。", answer);
        Assertions.assertEquals(2, modelClient.prompts().size());
    }

    @Test
    void deniesRiskyToolWhenApprovalPolicyIsNever() throws Exception {
        StubModelClient modelClient = new StubModelClient(
                "<tool>{\"name\":\"run_shell\",\"args\":{\"command\":\"echo hi\",\"timeout\":1}}</tool>",
                "<final>done</final>"
        );
        Pico pico = newPico(modelClient, ApprovalPolicy.NEVER, 3);

        String answer = pico.ask("run a command");

        Assertions.assertEquals("done", answer);
        Assertions.assertTrue(modelClient.prompts().get(1).contains("工具 run_shell 的执行审批被拒绝"));
    }

    private Pico newPico(StubModelClient modelClient, ApprovalPolicy approvalPolicy, int maxSteps) throws Exception {
        return new Pico(
                modelClient,
                new WorkspaceContext(tempDir, tempDir, "main", "main", "", List.of(), Map.of()),
                new SessionStore(tempDir.resolve(".pico/sessions")),
                approvalPolicy,
                maxSteps,
                128,
                Set.of()
        );
    }

    private static final class StubModelClient implements ModelClient {
        private final Deque<Object> responses = new ArrayDeque<>();
        private final List<String> prompts = new ArrayList<>();

        private StubModelClient(Object... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public String complete(String prompt, int maxNewTokens) throws IOException {
            prompts.add(prompt);
            Object next = responses.removeFirst();
            if (next instanceof IOException ioException) {
                throw ioException;
            }
            if (next instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return String.valueOf(next);
        }

        @Override
        public String providerName() {
            return "stub";
        }

        @Override
        public String modelName() {
            return "stub-model";
        }

        private List<String> prompts() {
            return prompts;
        }
    }
}
