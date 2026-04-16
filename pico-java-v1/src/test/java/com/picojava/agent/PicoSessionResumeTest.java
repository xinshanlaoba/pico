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

class PicoSessionResumeTest {
    @TempDir
    Path tempDir;

    @Test
    void restoresSessionAndCanContinueAsking() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "hello\n", StandardCharsets.UTF_8);
        SessionStore store = new SessionStore(tempDir.resolve(".pico/sessions"));
        Pico first = new Pico(
                new StubModelClient("<final>first answer</final>"),
                workspace(),
                store,
                ApprovalPolicy.AUTO,
                5,
                222,
                Set.of("OPENAI_API_KEY")
        );
        first.session().setDistilledMemory("remembered context");
        first.session().getMemoryState().put("scratch", "value");
        first.saveSession();

        String firstAnswer = first.ask("first question");
        Assertions.assertEquals("first answer", firstAnswer);

        StubModelClient resumedModel = new StubModelClient("<final>second answer</final>");
        Pico resumed = Pico.fromSession(
                resumedModel,
                workspace(),
                store,
                first.session().getId(),
                ApprovalPolicy.NEVER,
                1,
                1,
                Set.of()
        );

        Assertions.assertEquals(ApprovalPolicy.AUTO, resumed.approvalPolicy());
        Assertions.assertEquals(5, resumed.maxSteps());
        Assertions.assertEquals(222, resumed.maxNewTokens());
        Assertions.assertEquals("remembered context", resumed.session().getDistilledMemory());
        Assertions.assertEquals("remembered context", resumed.memory().getSummaryMemory());
        Assertions.assertEquals("value", resumed.memory().state().getExtensions().get("scratch"));
        Assertions.assertEquals(first.session().getLatestRunId(), resumed.session().getLatestRunId());
        Assertions.assertFalse(resumed.session().getRecentRuns().isEmpty());

        String secondAnswer = resumed.ask("second question");

        Assertions.assertEquals("second answer", secondAnswer);
        Assertions.assertEquals(1, resumedModel.prompts().size());
        Assertions.assertTrue(resumedModel.prompts().get(0).contains("first question"));
        Assertions.assertTrue(resumedModel.prompts().get(0).contains("first answer"));
        Assertions.assertTrue(resumedModel.prompts().get(0).contains("remembered context"));
        Assertions.assertEquals(2, resumed.session().getRecentRuns().size());
    }

    @Test
    void latestSessionCanBeResolvedAndResumed() throws Exception {
        SessionStore store = new SessionStore(tempDir.resolve(".pico/sessions"));
        Pico first = new Pico(new StubModelClient("<final>one</final>"), workspace(), store, ApprovalPolicy.AUTO, 3, 100, Set.of());
        first.ask("one");
        Pico second = new Pico(new StubModelClient("<final>two</final>"), workspace(), store, ApprovalPolicy.ASK, 4, 200, Set.of());
        second.ask("two");

        String latestSessionId = store.latest();
        Pico resumed = Pico.fromSession(
                new StubModelClient("<final>three</final>"),
                workspace(),
                store,
                latestSessionId,
                ApprovalPolicy.NEVER,
                1,
                1,
                Set.of()
        );

        Assertions.assertEquals(second.session().getId(), latestSessionId);
        Assertions.assertEquals(ApprovalPolicy.ASK, resumed.approvalPolicy());
        Assertions.assertEquals(4, resumed.maxSteps());
        Assertions.assertEquals(200, resumed.maxNewTokens());
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

    private static final class StubModelClient implements ModelClient {
        private final Deque<String> responses = new ArrayDeque<>();
        private final List<String> prompts = new ArrayList<>();

        private StubModelClient(String... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public String complete(String prompt, int maxNewTokens) throws IOException {
            prompts.add(prompt);
            if (responses.isEmpty()) {
                throw new IOException("no more responses");
            }
            return responses.removeFirst();
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
