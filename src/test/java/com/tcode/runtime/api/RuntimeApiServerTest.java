package com.tcode.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeApiServerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void exposesThreadTurnAndSseEvents(@TempDir Path tempDir) throws Exception {
        try (RuntimeThreadStore store = new RuntimeThreadStore(tempDir.resolve("runtime.db"));
             RuntimeApiServer server = new RuntimeApiServer(store, prompt -> "reply:" + prompt, 0, "secret")) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.port();

            HttpResponse<String> created = client.send(request(base + "/v1/threads", "POST", "")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, created.statusCode());
            String threadId = extract(created.body(), "thread_");

            HttpResponse<String> turn = client.send(request(base + "/v1/threads/" + threadId + "/turns", "POST",
                            "{\"input\":\"hello\"}").build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(202, turn.statusCode());

            String events = waitForEvents(client, base, threadId);
            assertTrue(events.contains("event: turn.started"));
            assertTrue(events.contains("event: message.delta"));
            assertTrue(events.contains("reply:hello"));
            assertTrue(events.contains("event: turn.completed"));
            assertTrue(events.contains("\"schema_version\":1"));
        }
    }

    @Test
    void persistsEventsEmittedByRuntimeTurnRunner(@TempDir Path tempDir) throws Exception {
        try (RuntimeThreadStore store = new RuntimeThreadStore(tempDir.resolve("runtime.db"));
             RuntimeApiServer server = new RuntimeApiServer(store, (input, events) -> {
                 events.emit("status.updated", "{\"phase\":\"thinking\"}");
                 events.emit("reasoning.delta", "{\"text\":\"inspect\"}");
                 events.emit("tool.call.started", "{\"name\":\"read_file\"}");
                 events.emit("tool.result", "{\"name\":\"read_file\",\"ok\":true}");
                 return "done:" + input;
             }, 0, "secret")) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.port();

            HttpResponse<String> created = client.send(request(base + "/v1/threads", "POST", "")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            String threadId = extract(created.body(), "thread_");

            HttpResponse<String> turn = client.send(request(base + "/v1/threads/" + threadId + "/turns", "POST",
                            "{\"input\":\"hello\"}").build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(202, turn.statusCode());

            String events = waitForEvents(client, base, threadId);
            assertTrue(events.contains("event: status.updated"));
            assertTrue(events.contains("\"phase\":\"thinking\""));
            assertTrue(events.contains("event: reasoning.delta"));
            assertTrue(events.contains("event: tool.call.started"));
            assertTrue(events.contains("event: tool.result"));
            assertTrue(events.contains("done:hello"));
            assertTrue(events.contains("event: turn.completed"));
        }
    }

    @Test
    void rejectsMissingApiKey(@TempDir Path tempDir) throws Exception {
        try (RuntimeThreadStore store = new RuntimeThreadStore(tempDir.resolve("runtime.db"));
             RuntimeApiServer server = new RuntimeApiServer(store, prompt -> "x", 0, "secret")) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/v1/threads"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(3))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(401, response.statusCode());
            JsonNode body = MAPPER.readTree(response.body());
            assertEquals("unauthorized", body.path("error").path("code").asText());
            assertEquals("unauthorized", body.path("error").path("message").asText());
        }
    }

    @Test
    void exposesPendingApprovalsAndAcceptsDecision(@TempDir Path tempDir) throws Exception {
        RuntimeHitlHandler hitl = new RuntimeHitlHandler();
        try (RuntimeThreadStore store = new RuntimeThreadStore(tempDir.resolve("runtime.db"));
             RuntimeApiServer server = new RuntimeApiServer(store, prompt -> "x", hitl, 0, "secret")) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.port();
            var result = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                    hitl.requestApproval(com.tcode.hitl.ApprovalRequest.of(
                            "write_file", "{\"path\":\"a.txt\"}", "test")));

            String approvals = waitForApproval(client, base);
            String approvalId = extract(approvals, "approval_");
            assertTrue(approvals.contains("\"tool\":\"write_file\""));

            HttpResponse<String> resolved = client.send(request(
                            base + "/v1/approvals/" + approvalId + "/decision",
                            "POST", "{\"decision\":\"APPROVED\"}").build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, resolved.statusCode());
            assertEquals(com.tcode.hitl.ApprovalResult.Decision.APPROVED, result.get().decision());
        }
    }

    private static HttpRequest.Builder request(String url, String method, String body) {
        HttpRequest.BodyPublisher publisher = body == null || body.isEmpty()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .header("Authorization", "Bearer secret")
                .header("Content-Type", "application/json")
                .method(method, publisher);
    }

    private static String waitForEvents(HttpClient client, String base, String threadId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            HttpResponse<String> response = client.send(request(base + "/v1/threads/" + threadId + "/events", "GET", "")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.body().contains("turn.completed")) {
                return response.body();
            }
            Thread.sleep(30);
        }
        fail("events did not complete");
        return "";
    }

    private static String extract(String body, String prefix) {
        int start = body.indexOf(prefix);
        assertTrue(start >= 0, body);
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    private static String waitForApproval(HttpClient client, String base) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            HttpResponse<String> response = client.send(request(base + "/v1/approvals", "GET", "")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.body().contains("approval_")) {
                return response.body();
            }
            Thread.sleep(30);
        }
        fail("approval did not appear");
        return "";
    }
}
