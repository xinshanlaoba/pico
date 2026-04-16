package com.picojava.agent;

import com.picojava.model.ModelClient;
import com.picojava.model.SpringAiOpenAiModelClient;
import com.picojava.session.MessageEntry;
import com.picojava.session.SessionStore;
import com.picojava.support.RecordingOpenAiServer;
import com.picojava.workspace.WorkspaceContext;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

class PicoSpringAiToolCallingTest {
    @TempDir
    Path tempDir;

    @Test
    void runtimeExecutesToolCallsReturnedBySpringAiAdapter() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "hello from spring ai\n", StandardCharsets.UTF_8);

        try (RecordingOpenAiServer server = new RecordingOpenAiServer(
                """
                {
                  "id": "chatcmpl-tool",
                  "object": "chat.completion",
                  "created": 1,
                  "model": "gpt-test",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "",
                        "tool_calls": [
                          {
                            "id": "call_readme",
                            "type": "function",
                            "function": {
                              "name": "read_file",
                              "arguments": "{\\"path\\":\\"README.md\\",\\"start\\":1,\\"end\\":5}"
                            }
                          }
                        ]
                      },
                      "finish_reason": "tool_calls"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 11,
                    "completion_tokens": 4,
                    "total_tokens": 15
                  }
                }
                """,
                """
                {
                  "id": "chatcmpl-final",
                  "object": "chat.completion",
                  "created": 2,
                  "model": "gpt-test",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "<final>done</final>"
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 18,
                    "completion_tokens": 5,
                    "total_tokens": 23
                  }
                }
                """
        )) {
            ModelClient modelClient = new SpringAiOpenAiModelClient(
                    "gpt-test",
                    server.baseUrl(),
                    "sk-test",
                    0.2,
                    0.95,
                    Duration.ofSeconds(5),
                    0,
                    ObservationRegistry.NOOP
            );
            Pico pico = new Pico(
                    modelClient,
                    new WorkspaceContext(tempDir, tempDir, "main", "main", "", List.of(), Map.of()),
                    new SessionStore(tempDir.resolve(".pico/sessions")),
                    ApprovalPolicy.AUTO,
                    4,
                    128,
                    Set.of()
            );

            String answer = pico.ask("inspect the readme");

            Assertions.assertEquals("done", answer);
            Assertions.assertEquals(2, server.requestBodies().size());
            Assertions.assertTrue(server.requestBodies().get(0).contains("\"tools\""));
            Assertions.assertTrue(server.requestBodies().get(0).contains("\"read_file\""));
            MessageEntry toolMessage = pico.session().getHistory().stream()
                    .filter(entry -> "tool".equals(entry.getRole()))
                    .findFirst()
                    .orElseThrow();
            Assertions.assertTrue(toolMessage.getContent().contains("hello from spring ai"));
        }
    }
}
