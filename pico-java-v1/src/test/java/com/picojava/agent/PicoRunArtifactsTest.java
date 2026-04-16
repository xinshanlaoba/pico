package com.picojava.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.picojava.common.JsonUtils;
import com.picojava.model.ModelClient;
import com.picojava.model.ModelResponse;
import com.picojava.model.ModelUsage;
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
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

class PicoRunArtifactsTest {
    @TempDir
    Path tempDir;

    @Test
    void writesRunArtifactsForSuccessfulAsk() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "hello\n", StandardCharsets.UTF_8);
        Pico pico = new Pico(
                new StubModelClient(
                        "<tool>{\"name\":\"list_files\",\"args\":{\"path\":\".\"}}</tool>",
                        "<final>done</final>"
                ),
                workspace(),
                new SessionStore(tempDir.resolve(".pico/sessions")),
                ApprovalPolicy.AUTO,
                4,
                128,
                Set.of()
        );

        String answer = pico.ask("inspect workspace");

        Assertions.assertEquals("done", answer);
        Path runDir = onlyRunDir();

        JsonNode taskState = readJson(runDir.resolve("task_state.json"));
        Assertions.assertEquals("completed", taskState.path("status").asText());
        Assertions.assertEquals("inspect workspace", taskState.path("user_input").asText());
        Assertions.assertEquals("done", taskState.path("final_answer").asText());
        Assertions.assertEquals(2, taskState.path("model_call_count").asInt());
        Assertions.assertEquals(1, taskState.path("tool_call_count").asInt());
        Assertions.assertEquals(2, taskState.path("steps").size());

        List<String> traceLines = Files.readAllLines(runDir.resolve("trace.jsonl"), StandardCharsets.UTF_8);
        Assertions.assertFalse(traceLines.isEmpty());
        Assertions.assertTrue(traceLines.stream().anyMatch(line -> line.contains("\"event_type\":\"run_started\"")));
        Assertions.assertTrue(traceLines.stream().anyMatch(line -> line.contains("\"event_type\":\"tool_execution_completed\"")));
        Assertions.assertTrue(traceLines.stream().anyMatch(line -> line.contains("\"event_type\":\"run_finished\"")));

        JsonNode report = readJson(runDir.resolve("report.json"));
        Assertions.assertEquals("completed", report.path("status").asText());
        Assertions.assertEquals("done", report.path("final_answer").asText());
        Assertions.assertEquals(2, report.path("step_count").asInt());
        Assertions.assertEquals(2, report.path("steps").size());
    }

    @Test
    void writesFailureArtifactsWhenModelCallThrows() throws Exception {
        Pico pico = new Pico(
                new StubModelClient(new IOException("boom")),
                workspace(),
                new SessionStore(tempDir.resolve(".pico/sessions")),
                ApprovalPolicy.AUTO,
                2,
                64,
                Set.of()
        );

        IOException error = Assertions.assertThrows(IOException.class, () -> pico.ask("fail"));
        Assertions.assertEquals("boom", error.getMessage());

        Path runDir = onlyRunDir();
        JsonNode taskState = readJson(runDir.resolve("task_state.json"));
        Assertions.assertEquals("failed", taskState.path("status").asText());
        Assertions.assertTrue(taskState.path("error").asText().contains("boom"));
        Assertions.assertEquals(1, taskState.path("model_call_count").asInt());

        List<String> traceLines = Files.readAllLines(runDir.resolve("trace.jsonl"), StandardCharsets.UTF_8);
        Assertions.assertTrue(traceLines.stream().anyMatch(line -> line.contains("\"event_type\":\"model_call_failed\"")));
        Assertions.assertTrue(traceLines.stream().anyMatch(line -> line.contains("\"event_type\":\"run_failed\"")));

        JsonNode report = readJson(runDir.resolve("report.json"));
        Assertions.assertEquals("failed", report.path("status").asText());
        Assertions.assertTrue(report.path("error").asText().contains("boom"));
    }

    @Test
    void writesModelUsageAndStopReasonIntoTaskState() throws Exception {
        Pico pico = new Pico(
                new UsageStubModelClient(),
                workspace(),
                new SessionStore(tempDir.resolve(".pico/sessions")),
                ApprovalPolicy.AUTO,
                2,
                64,
                Set.of()
        );

        String answer = pico.ask("summarize");

        Assertions.assertEquals("done", answer);
        Path runDir = onlyRunDir();
        JsonNode taskState = readJson(runDir.resolve("task_state.json"));
        JsonNode modelCall = taskState.path("steps").get(0).path("model_call");
        Assertions.assertEquals("stop", modelCall.path("stop_reason").asText());
        Assertions.assertEquals(11, modelCall.path("input_tokens").asInt());
        Assertions.assertEquals(5, modelCall.path("output_tokens").asInt());
        Assertions.assertEquals(16, modelCall.path("total_tokens").asInt());
        Assertions.assertTrue(modelCall.path("raw_response").asText().contains("\"raw\""));
    }

    private WorkspaceContext workspace() {
        return new WorkspaceContext(
                tempDir,
                tempDir,
                "main",
                "main",
                "",
                List.of(),
                Map.of()
        );
    }

    private Path onlyRunDir() throws IOException {
        try (var stream = Files.list(tempDir.resolve(".pico/runs"))) {
            return stream.findFirst().orElseThrow();
        }
    }

    private JsonNode readJson(Path path) throws IOException {
        return JsonUtils.MAPPER.readTree(path.toFile());
    }

    private static final class StubModelClient implements ModelClient {
        private final Deque<Object> responses = new ArrayDeque<>();

        private StubModelClient(Object... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public String complete(String prompt, int maxNewTokens) throws IOException {
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
    }

    private static final class UsageStubModelClient implements ModelClient {
        @Override
        public ModelResponse completeResponse(String prompt, int maxNewTokens) {
            return new ModelResponse(
                    "<final>done</final>",
                    "{\"raw\":true}",
                    new ModelUsage(11, 5, 16),
                    "stop"
            );
        }

        @Override
        public String complete(String prompt, int maxNewTokens) {
            return "<final>done</final>";
        }

        @Override
        public String providerName() {
            return "stub";
        }

        @Override
        public String modelName() {
            return "usage-model";
        }
    }
}
