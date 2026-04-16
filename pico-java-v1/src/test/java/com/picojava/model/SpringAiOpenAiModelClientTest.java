package com.picojava.model;

import com.picojava.support.RecordingOpenAiServer;
import com.picojava.tool.impl.ListFilesTool;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

class SpringAiOpenAiModelClientTest {
    @Test
    void mapsTextResponsesAndAttachesToolDefinitions() throws Exception {
        try (RecordingOpenAiServer server = new RecordingOpenAiServer(
                """
                {
                  "id": "chatcmpl-text",
                  "object": "chat.completion",
                  "created": 1,
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
                    "prompt_tokens": 12,
                    "completion_tokens": 5,
                    "total_tokens": 17
                  }
                }
                """
        )) {
            SpringAiOpenAiModelClient client = new SpringAiOpenAiModelClient(
                    "gpt-test",
                    server.baseUrl(),
                    "sk-test",
                    0.2,
                    0.95,
                    Duration.ofSeconds(5),
                    0,
                    ObservationRegistry.NOOP
            );

            ModelResponse response = client.completeResponse(ModelRequest.withTools(
                    "inspect repo",
                    64,
                    List.of(new ListFilesTool())
            ));

            Assertions.assertEquals("<final>done</final>", response.textContent());
            Assertions.assertEquals("stop", response.stopReason());
            Assertions.assertNotNull(response.usage());
            Assertions.assertEquals(12, response.usage().inputTokens());
            Assertions.assertEquals(5, response.usage().outputTokens());
            Assertions.assertEquals(17, response.usage().totalTokens());
            Assertions.assertTrue(response.rawPayload().contains("\"model\":\"gpt-test\""));
            Assertions.assertTrue(server.requestBodies().get(0).contains("\"tools\""));
            Assertions.assertTrue(server.requestBodies().get(0).contains("\"list_files\""));
        }
    }

    @Test
    void mapsNativeToolCallsBackToLegacyToolEnvelope() throws Exception {
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
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "list_files",
                              "arguments": "{\\"path\\":\\"src\\"}"
                            }
                          }
                        ]
                      },
                      "finish_reason": "tool_calls"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 3,
                    "total_tokens": 13
                  }
                }
                """
        )) {
            SpringAiOpenAiModelClient client = new SpringAiOpenAiModelClient(
                    "gpt-test",
                    server.baseUrl(),
                    "sk-test",
                    0.2,
                    0.95,
                    Duration.ofSeconds(5),
                    0,
                    ObservationRegistry.NOOP
            );

            ModelResponse response = client.completeResponse(ModelRequest.withTools(
                    "list the source tree",
                    64,
                    List.of(new ListFilesTool())
            ));

            Assertions.assertEquals("<tool>{\"name\":\"list_files\",\"args\":{\"path\":\"src\"}}</tool>", response.textContent());
            Assertions.assertEquals("tool_calls", response.stopReason());
            Assertions.assertTrue(response.rawPayload().contains("\"tool_calls\""));
        }
    }
}
