package com.picojava.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSession;

class ModelClientAdaptersTest {
    @Test
    void openAiCompatibleClientBuildsRequestAndParsesUnifiedResponse() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(
                new StubHttpResponse(
                        200,
                        """
                        {
                          "output_text": "<final>done</final>",
                          "choices": [
                            {
                              "finish_reason": "stop",
                              "message": {
                                "content": "<final>done</final>"
                              }
                            }
                          ],
                          "usage": {
                            "input_tokens": 12,
                            "output_tokens": 5,
                            "total_tokens": 17
                          }
                        }
                        """
                )
        );
        OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                "gpt-test",
                "https://example.test",
                "sk-test",
                0.2,
                Duration.ofSeconds(5),
                0,
                executor
        );

        ModelResponse response = client.completeResponse("inspect repo", 64);
        HttpRequest request = executor.requests().get(0);

        Assertions.assertEquals("<final>done</final>", response.textContent());
        Assertions.assertEquals("stop", response.stopReason());
        Assertions.assertNotNull(response.usage());
        Assertions.assertEquals(12, response.usage().inputTokens());
        Assertions.assertEquals(5, response.usage().outputTokens());
        Assertions.assertEquals(URI.create("https://example.test/v1/responses"), request.uri());
        Assertions.assertEquals("Bearer sk-test", request.headers().firstValue("Authorization").orElseThrow());
        Assertions.assertTrue(readRequestBody(request).contains("\"max_output_tokens\":64"));
    }

    @Test
    void anthropicClientRetriesOnRetriableStatus() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(
                new StubHttpResponse(429, "{\"error\":{\"message\":\"rate limited\"}}"),
                new StubHttpResponse(
                        200,
                        """
                        {
                          "content": [
                            {"type":"text","text":"<final>anthropic ok</final>"}
                          ],
                          "stop_reason": "end_turn",
                          "usage": {
                            "input_tokens": 9,
                            "output_tokens": 4
                          }
                        }
                        """
                )
        );
        AnthropicCompatibleModelClient client = new AnthropicCompatibleModelClient(
                "claude-test",
                "https://anthropic.test",
                "ak-test",
                0.1,
                Duration.ofSeconds(5),
                1,
                executor
        );

        ModelResponse response = client.completeResponse("continue", 32);

        Assertions.assertEquals(2, executor.requests().size());
        Assertions.assertEquals("<final>anthropic ok</final>", response.textContent());
        Assertions.assertEquals("end_turn", response.stopReason());
        Assertions.assertNotNull(response.usage());
        Assertions.assertEquals(13, response.usage().totalTokens());
    }

    @Test
    void ollamaClientRetriesOnTimeoutAndExposesUsage() throws Exception {
        RecordingExecutor executor = new RecordingExecutor(
                new HttpTimeoutException("timeout"),
                new StubHttpResponse(
                        200,
                        """
                        {
                          "response": "<tool>{\\"name\\":\\"list_files\\",\\"args\\":{\\"path\\":\\".\\"}}</tool>",
                          "done_reason": "stop",
                          "prompt_eval_count": 21,
                          "eval_count": 8
                        }
                        """
                )
        );
        OllamaModelClient client = new OllamaModelClient(
                "qwen-test",
                "http://ollama.test",
                0.0,
                0.9,
                Duration.ofSeconds(5),
                1,
                executor
        );

        ModelResponse response = client.completeResponse("list files", 48);

        Assertions.assertEquals(2, executor.requests().size());
        Assertions.assertTrue(response.textContent().contains("list_files"));
        Assertions.assertEquals("stop", response.stopReason());
        Assertions.assertEquals(21, response.usage().inputTokens());
        Assertions.assertEquals(8, response.usage().outputTokens());
    }

    @Test
    void badRequestProducesStructuredModelClientException() {
        RecordingExecutor executor = new RecordingExecutor(
                new StubHttpResponse(400, "{\"error\":{\"message\":\"bad prompt\"}}")
        );
        OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                "gpt-test",
                "https://example.test",
                "",
                0.2,
                Duration.ofSeconds(5),
                0,
                executor
        );

        ModelClientException error = Assertions.assertThrows(
                ModelClientException.class,
                () -> client.completeResponse("broken", 32)
        );

        Assertions.assertEquals("openai", error.getProvider());
        Assertions.assertEquals(400, error.getStatusCode());
        Assertions.assertFalse(error.isRetryable());
        Assertions.assertTrue(error.getResponseBody().contains("bad prompt"));
    }

    private static String readRequestBody(HttpRequest request) throws Exception {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CountDownLatch completed = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                output.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                completed.countDown();
            }

            @Override
            public void onComplete() {
                completed.countDown();
            }
        });
        Assertions.assertTrue(completed.await(2, TimeUnit.SECONDS));
        return output.toString(StandardCharsets.UTF_8);
    }

    private static final class RecordingExecutor implements HttpExecutor {
        private final Deque<QueuedResult> results = new ArrayDeque<>();
        private final List<HttpRequest> requests = new ArrayList<>();

        private RecordingExecutor(Object... results) {
            for (Object result : results) {
                if (result instanceof IOException ioException) {
                    this.results.addLast(QueuedResult.io(ioException));
                } else if (result instanceof InterruptedException interruptedException) {
                    this.results.addLast(QueuedResult.interrupted(interruptedException));
                } else {
                    this.results.addLast(QueuedResult.response(castResponse(result)));
                }
            }
        }

        @Override
        public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
            requests.add(request);
            QueuedResult next = results.removeFirst();
            if (next.ioException != null) {
                throw next.ioException;
            }
            if (next.interruptedException != null) {
                throw next.interruptedException;
            }
            return next.response;
        }

        private List<HttpRequest> requests() {
            return requests;
        }

        @SuppressWarnings("unchecked")
        private HttpResponse<String> castResponse(Object value) {
            return (HttpResponse<String>) value;
        }

        private static final class QueuedResult {
            private final HttpResponse<String> response;
            private final IOException ioException;
            private final InterruptedException interruptedException;

            private QueuedResult(HttpResponse<String> response, IOException ioException, InterruptedException interruptedException) {
                this.response = response;
                this.ioException = ioException;
                this.interruptedException = interruptedException;
            }

            private static QueuedResult response(HttpResponse<String> response) {
                return new QueuedResult(response, null, null);
            }

            private static QueuedResult io(IOException exception) {
                return new QueuedResult(null, exception, null);
            }

            private static QueuedResult interrupted(InterruptedException exception) {
                return new QueuedResult(null, null, exception);
            }
        }
    }

    private static final class StubHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;

        private StubHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (left, right) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://example.test");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
