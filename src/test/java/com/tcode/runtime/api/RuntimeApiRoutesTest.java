package com.tcode.runtime.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeApiRoutesTest {

    @Test
    void threadRoutesCreateThreadAndExposeSseEvents(@TempDir Path tempDir) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (RuntimeThreadStore store = new RuntimeThreadStore(tempDir.resolve("runtime.db"))) {
            RuntimeThreadRoutes routes = new RuntimeThreadRoutes(store, (input, events) -> "reply:" + input, executor);

            RuntimeApiResult created = routes.handle(new RuntimeApiRequest("POST", "/v1/threads", null, ""));
            assertEquals(200, created.status());
            String threadId = extract(created.body(), "thread_");

            RuntimeApiResult events = routes.handle(new RuntimeApiRequest(
                    "GET", "/v1/threads/" + threadId + "/events", "after=0", ""));
            assertEquals(200, events.status());
            assertEquals("text/event-stream; charset=utf-8", events.contentType());
            assertTrue(events.body().contains("event: thread.created"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void approvalRoutesReportMissingConfiguration() {
        RuntimeApprovalRoutes routes = new RuntimeApprovalRoutes(null);

        RuntimeApiResult result = routes.handle(new RuntimeApiRequest("GET", "/v1/approvals", null, ""));

        assertEquals(404, result.status());
        assertTrue(result.body().contains("approvals_not_configured"));
    }

    private static String extract(String body, String prefix) {
        int start = body.indexOf(prefix);
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
