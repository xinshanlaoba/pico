package com.picojava.agent;

import com.picojava.model.ModelClient;
import com.picojava.session.SessionStore;
import com.picojava.workspace.WorkspaceContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ContextManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void includesAppendedSessionHistoryInPrompt() throws Exception {
        Pico pico = newPico("clean");
        pico.appendHistory("assistant", "Checked the repository layout.");
        pico.appendHistory("tool", "list_files -> src/main/java");

        ContextManager.BuildResult result = pico.contextManager().build("inspect the project");

        Assertions.assertTrue(result.prompt().contains("会话历史："));
        Assertions.assertTrue(result.prompt().contains("[assistant] Checked the repository layout."));
        Assertions.assertTrue(result.prompt().contains("[tool] list_files -> src/main/java"));
        Assertions.assertEquals(2, ((Number) result.metadata().get("history_messages_included")).intValue());
    }

    @Test
    void triggersCompressionAndKeepsMostRecentMessages() throws Exception {
        Pico pico = newPico(("M src/Main.java\n").repeat(500));
        pico.memory().setSummaryMemory("Stable summary memory. " + "s".repeat(1100));
        for (int i = 0; i < 16; i++) {
            pico.appendHistory("assistant", "message-" + i + " " + "x".repeat(700));
        }
        for (int i = 0; i < 6; i++) {
            pico.rememberToolResult(ToolExecutionResult.success(
                    new ToolCall("read_file", Map.of("path", "README-" + i + ".md")),
                    "y".repeat(2400),
                    false,
                    true
            ));
        }

        ContextManager.BuildResult result = pico.contextManager().build("current request " + "q".repeat(3200));

        Assertions.assertTrue((Boolean) result.metadata().get("compressed"));
        Assertions.assertTrue(((Number) result.metadata().get("history_messages_omitted")).intValue() > 0);
        Assertions.assertTrue(result.prompt().contains("Stable summary memory."));
        Assertions.assertTrue(result.prompt().contains("message-15"));
        Assertions.assertFalse(result.prompt().contains("message-0"));
    }

    @Test
    void rendersPromptFragmentsWithTruncatedToolResults() throws Exception {
        Pico pico = newPico("clean");
        pico.memory().setSummaryMemory("Remember the repository conventions.");
        pico.rememberToolResult(ToolExecutionResult.success(
                new ToolCall("read_file", Map.of("path", "docs/guide.md")),
                "z".repeat(1200),
                false,
                true
        ));

        ContextManager.BuildResult result = pico.contextManager().build("summarize the guide");

        Assertions.assertTrue(result.prompt().contains("工作区摘要："));
        Assertions.assertTrue(result.prompt().contains("摘要记忆："));
        Assertions.assertTrue(result.prompt().contains("最近工具结果："));
        Assertions.assertTrue(result.prompt().contains("当前用户请求："));
        Assertions.assertTrue(result.prompt().contains("docs/guide.md"));
        Assertions.assertTrue(result.prompt().contains("...[已截断"));
    }

    private Pico newPico(String status) throws Exception {
        return new Pico(
                new NoopModelClient(),
                new WorkspaceContext(
                        tempDir,
                        tempDir,
                        "main",
                        "main",
                        status,
                        List.of("abc123 initial commit"),
                        Map.of("README.md", "Project documentation")
                ),
                new SessionStore(tempDir.resolve(".pico/sessions")),
                ApprovalPolicy.AUTO,
                6,
                256,
                Set.of()
        );
    }

    private static final class NoopModelClient implements ModelClient {
        @Override
        public String complete(String prompt, int maxNewTokens) throws IOException {
            return "<final>unused</final>";
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
}
