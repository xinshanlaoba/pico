package com.picojava.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.picojava.common.JsonUtils;
import com.picojava.model.ModelClient;
import com.picojava.session.MessageEntry;
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
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

class PicoDelegateToolTest {
    @TempDir
    Path tempDir;

    @Test
    void delegateToolRunsChildAgentAndLinksChildRun() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "delegate demo\n", StandardCharsets.UTF_8);
        StubModelClient modelClient = new StubModelClient(
                "<tool>{\"name\":\"delegate\",\"args\":{\"task\":\"inspect README.md\",\"max_steps\":2}}</tool>",
                "<final>README summary from child</final>",
                "<final>parent used the child result</final>"
        );
        Pico pico = newPico(modelClient);

        String answer = pico.ask("delegate the investigation");

        Assertions.assertEquals("parent used the child result", answer);
        Assertions.assertEquals(3, modelClient.prompts().size());
        Assertions.assertTrue(modelClient.prompts().get(1).contains("inspect README.md"));

        MessageEntry delegateEvent = pico.session().getHistory().stream()
                .filter(entry -> "tool".equals(entry.getRole()) && entry.getContent().contains("delegate 结果"))
                .findFirst()
                .orElseThrow();
        Assertions.assertTrue(delegateEvent.getContent().contains("子 run id："));
        Assertions.assertTrue(delegateEvent.getContent().contains("README summary from child"));

        List<JsonNode> taskStates = loadTaskStates();
        JsonNode parent = taskStates.stream().filter(node -> node.path("parent_run_id").asText().isBlank()).findFirst().orElseThrow();
        JsonNode child = taskStates.stream().filter(node -> !node.path("parent_run_id").asText().isBlank()).findFirst().orElseThrow();

        Assertions.assertEquals(parent.path("run_id").asText(), child.path("parent_run_id").asText());
        Assertions.assertEquals(1, parent.path("child_runs").size());
        Assertions.assertEquals(child.path("run_id").asText(), parent.path("child_runs").get(0).path("run_id").asText());
        Assertions.assertEquals(1, child.path("delegate_depth").asInt());
        Assertions.assertTrue(Files.exists(
                tempDir.resolve(".pico/runs")
                        .resolve(parent.path("run_id").asText())
                        .resolve("children")
                        .resolve(child.path("run_id").asText() + ".json")
        ));
    }

    @Test
    void childCannotDelegateAgainWhenDepthLimitIsReached() throws Exception {
        StubModelClient modelClient = new StubModelClient(
                "<tool>{\"name\":\"delegate\",\"args\":{\"task\":\"subtask\",\"max_steps\":2}}</tool>",
                "<tool>{\"name\":\"delegate\",\"args\":{\"task\":\"nested subtask\",\"max_steps\":1}}</tool>",
                "<final>child recovered without nesting</final>",
                "<final>parent finished</final>"
        );
        Pico pico = newPico(modelClient);

        String answer = pico.ask("start delegate");

        Assertions.assertEquals("parent finished", answer);
        Assertions.assertEquals(4, modelClient.prompts().size());
        Assertions.assertTrue(modelClient.prompts().get(2).contains("delegate 深度已超过上限"));
    }

    private Pico newPico(StubModelClient modelClient) throws Exception {
        return new Pico(
                modelClient,
                new WorkspaceContext(tempDir, tempDir, "main", "main", "", List.of(), Map.of()),
                new SessionStore(tempDir.resolve(".pico/sessions")),
                ApprovalPolicy.AUTO,
                4,
                128,
                Set.of()
        );
    }

    private List<JsonNode> loadTaskStates() throws IOException {
        try (var stream = Files.list(tempDir.resolve(".pico/runs"))) {
            return stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> readJson(path.resolve("task_state.json")))
                    .toList();
        }
    }

    private JsonNode readJson(Path path) {
        try {
            return JsonUtils.MAPPER.readTree(path.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
