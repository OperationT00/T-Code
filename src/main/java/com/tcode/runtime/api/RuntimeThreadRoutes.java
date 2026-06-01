package com.tcode.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.ExecutorService;

public final class RuntimeThreadRoutes {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RuntimeThreadStore store;
    private final RuntimeTurnRunner runner;
    private final ExecutorService executor;

    public RuntimeThreadRoutes(RuntimeThreadStore store, RuntimeTurnRunner runner, ExecutorService executor) {
        this.store = store;
        this.runner = runner == null ? (input, events) -> "" : runner;
        this.executor = executor;
    }

    public RuntimeApiResult handle(RuntimeApiRequest request) {
        try {
            if ("POST".equals(request.method()) && "/v1/threads".equals(request.path())) {
                return RuntimeApiResult.json(200, RuntimeApiResponses.threadCreated(store.createThread()));
            }
            if ("POST".equals(request.method()) && request.path().matches("/v1/threads/[^/]+/turns")) {
                return handleTurn(request, segment(request.path(), 3));
            }
            if ("GET".equals(request.method()) && request.path().matches("/v1/threads/[^/]+/events")) {
                return handleEvents(request, segment(request.path(), 3));
            }
            return RuntimeApiResult.json(404, RuntimeApiResponses.error("not_found"));
        } catch (Exception e) {
            return RuntimeApiResult.json(500, RuntimeApiResponses.error("internal_error", e.getMessage()));
        }
    }

    private RuntimeApiResult handleTurn(RuntimeApiRequest request, String threadId) throws Exception {
        if (!store.exists(threadId)) {
            return RuntimeApiResult.json(404, RuntimeApiResponses.error("thread_not_found"));
        }
        JsonNode body = MAPPER.readTree(request.body());
        String input = body.path("input").asText("");
        if (input.isBlank()) {
            return RuntimeApiResult.json(400, RuntimeApiResponses.error("input_required"));
        }
        String turnId = "turn_" + Long.toHexString(System.nanoTime());
        store.appendEvent(threadId, "turn.started", RuntimeEventPayloads.turnStarted(turnId, input));
        executor.submit(() -> runTurn(threadId, turnId, input));
        return RuntimeApiResult.json(202, RuntimeApiResponses.turnAccepted(turnId));
    }

    private void runTurn(String threadId, String turnId, String input) {
        try {
            RuntimeEventSink sink = (type, data) -> store.appendEvent(threadId, type, data);
            String result = runner.run(input, sink);
            store.appendEvent(threadId, "message.delta", RuntimeEventPayloads.messageDelta(turnId, result));
            store.appendEvent(threadId, "turn.completed", RuntimeEventPayloads.turnCompleted(turnId));
        } catch (Exception e) {
            store.appendEvent(threadId, "turn.failed", RuntimeEventPayloads.turnFailed(turnId, e.getMessage()));
        }
    }

    private RuntimeApiResult handleEvents(RuntimeApiRequest request, String threadId) {
        if (!store.exists(threadId)) {
            return RuntimeApiResult.json(404, RuntimeApiResponses.error("thread_not_found"));
        }
        List<RuntimeEvent> events = store.events(threadId, parseAfter(request.query()));
        return RuntimeApiResult.sse(formatSse(events));
    }

    private static String segment(String path, int index) {
        String[] parts = path.split("/");
        return parts.length > index ? parts[index] : "";
    }

    private static long parseAfter(String query) {
        if (query == null || query.isBlank()) {
            return 0;
        }
        for (String part : query.split("&")) {
            if (part.startsWith("after=")) {
                try {
                    return Long.parseLong(part.substring("after=".length()));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static String formatSse(List<RuntimeEvent> events) {
        StringBuilder sb = new StringBuilder();
        for (RuntimeEvent event : events) {
            sb.append("id: ").append(event.id()).append('\n');
            sb.append("event: ").append(event.type()).append('\n');
            sb.append("data: ").append(event.data()).append("\n\n");
        }
        return sb.toString();
    }
}
