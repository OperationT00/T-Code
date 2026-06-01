package com.tcode.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcode.hitl.ApprovalResult;

public final class RuntimeApprovalRoutes {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RuntimeHitlHandler hitlHandler;

    public RuntimeApprovalRoutes(RuntimeHitlHandler hitlHandler) {
        this.hitlHandler = hitlHandler;
    }

    public RuntimeApiResult handle(RuntimeApiRequest request) {
        try {
            if (hitlHandler == null) {
                return json(404, RuntimeApiResponses.error("approvals_not_configured"));
            }
            if ("GET".equals(request.method()) && "/v1/approvals".equals(request.path())) {
                return json(200, RuntimeApiResponses.pendingApprovals(hitlHandler.pendingApprovals()));
            }
            if ("POST".equals(request.method()) && request.path().matches("/v1/approvals/[^/]+/decision")) {
                return handleDecision(request);
            }
            return json(404, RuntimeApiResponses.error("not_found"));
        } catch (IllegalArgumentException e) {
            return json(400, RuntimeApiResponses.error("invalid_request", e.getMessage()));
        } catch (Exception e) {
            return json(500, RuntimeApiResponses.error("internal_error", e.getMessage()));
        }
    }

    private RuntimeApiResult handleDecision(RuntimeApiRequest request) throws Exception {
        String approvalId = segment(request.path(), 3);
        ApprovalResult result = approvalResult(MAPPER.readTree(request.body()));
        if (!hitlHandler.resolve(approvalId, result)) {
            return json(404, RuntimeApiResponses.error("approval_not_found"));
        }
        return json(200, RuntimeApiResponses.approvalResolved(approvalId, result.decision()));
    }

    private static ApprovalResult approvalResult(JsonNode body) {
        String decision = body.path("decision").asText("");
        return switch (decision) {
            case "APPROVED" -> ApprovalResult.approve();
            case "APPROVED_ALL" -> ApprovalResult.approveAll();
            case "APPROVED_ALL_BY_SERVER" -> ApprovalResult.approveAllByServer();
            case "REJECTED" -> ApprovalResult.reject(body.path("reason").asText(""));
            case "MODIFIED" -> ApprovalResult.modify(body.path("modified_arguments").asText(""));
            case "SKIPPED" -> ApprovalResult.skip();
            default -> throw new IllegalArgumentException("invalid_decision");
        };
    }

    private static String segment(String path, int index) {
        String[] parts = path.split("/");
        return parts.length > index ? parts[index] : "";
    }

    private static RuntimeApiResult json(int status, String body) {
        return RuntimeApiResult.json(status, body);
    }
}
