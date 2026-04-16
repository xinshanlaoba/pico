package com.picojava.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RecordingOpenAiServer implements AutoCloseable {
    private final HttpServer server;
    private final Deque<String> responses = new ArrayDeque<>();
    private final List<String> requestBodies = new ArrayList<>();

    public RecordingOpenAiServer(String... responses) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.responses.addAll(List.of(responses));
        this.server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                byte[] requestBytes = exchange.getRequestBody().readAllBytes();
                synchronized (requestBodies) {
                    requestBodies.add(new String(requestBytes, StandardCharsets.UTF_8));
                }

                String responseBody;
                synchronized (this.responses) {
                    if (this.responses.isEmpty()) {
                        responseBody = "{\"error\":\"no queued response\"}";
                        exchange.sendResponseHeaders(500, responseBody.getBytes(StandardCharsets.UTF_8).length);
                    } else {
                        responseBody = this.responses.removeFirst();
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
                    }
                }

                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBody.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        this.server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<String> requestBodies() {
        synchronized (requestBodies) {
            return List.copyOf(requestBodies);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
