package com.tcode.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcode.hitl.ApprovalRequest;
import com.tcode.hitl.ApprovalResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeApiResponsesTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildsSuccessfulRuntimeApiResponses() throws Exception {
        JsonNode thread = MAPPER.readTree(RuntimeApiResponses.threadCreated("thread_1"));
        assertEquals("thread_1", thread.path("id").asText());
        assertEquals("thread", thread.path("object").asText());

        JsonNode turn = MAPPER.readTree(RuntimeApiResponses.turnAccepted("turn_1"));
        assertEquals("turn_1", turn.path("id").asText());
        assertEquals("turn", turn.path("object").asText());
        assertEquals("running", turn.path("status").asText());

        JsonNode resolved = MAPPER.readTree(RuntimeApiResponses.approvalResolved(
                "approval_1", ApprovalResult.Decision.APPROVED));
        assertEquals("approval_1", resolved.path("id").asText());
        assertEquals("resolved", resolved.path("status").asText());
        assertEquals("APPROVED", resolved.path("decision").asText());

        RuntimeHitlHandler.PendingApproval pending = new RuntimeHitlHandler.PendingApproval(
                "approval_1",
                ApprovalRequest.of("write_file", "{\"path\":\"a.txt\"}", "test"),
                new CompletableFuture<>());
        JsonNode approvals = MAPPER.readTree(RuntimeApiResponses.pendingApprovals(List.of(pending)));
        assertEquals("approval_1", approvals.path("data").path(0).path("id").asText());
        assertEquals("write_file", approvals.path("data").path(0).path("tool").asText());
    }
}
