package com.tcode.runtime.api;

import com.tcode.hitl.ApprovalRequest;
import com.tcode.hitl.ApprovalResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeHitlHandlerTest {

    @Test
    void blocksUntilPendingApprovalIsResolved() throws Exception {
        RuntimeHitlHandler handler = new RuntimeHitlHandler();
        CompletableFuture<ApprovalResult> result = CompletableFuture.supplyAsync(() ->
                handler.requestApproval(ApprovalRequest.of("write_file", "{\"path\":\"a.txt\"}", "test")));

        RuntimeHitlHandler.PendingApproval pending = waitForPending(handler);
        assertEquals("write_file", pending.request().toolName());
        assertTrue(handler.resolve(pending.id(), ApprovalResult.approve()));
        assertEquals(ApprovalResult.Decision.APPROVED, result.get().decision());
        assertTrue(handler.pendingApprovals().isEmpty());
    }

    @Test
    void rejectsUnknownApprovalId() {
        RuntimeHitlHandler handler = new RuntimeHitlHandler();

        assertFalse(handler.resolve("missing", ApprovalResult.approve()));
    }

    private static RuntimeHitlHandler.PendingApproval waitForPending(RuntimeHitlHandler handler) throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            while (handler.pendingApprovals().isEmpty()) {
                Thread.sleep(10);
            }
        });
        return handler.pendingApprovals().get(0);
    }
}
