package com.tcode.runtime.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tcode.hitl.ApprovalResult;

import java.util.List;

final class RuntimeApiResponses {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RuntimeApiResponses() {
    }

    static String error(String code) {
        return error(code, code);
    }

    static String error(String code, String message) {
        ObjectNode error = MAPPER.createObjectNode();
        error.put("code", code);
        error.put("message", message == null ? "" : message);
        ObjectNode response = MAPPER.createObjectNode();
        response.set("error", error);
        return response.toString();
    }

    static String threadCreated(String id) {
        return MAPPER.createObjectNode()
                .put("id", id)
                .put("object", "thread")
                .toString();
    }

    static String turnAccepted(String id) {
        return MAPPER.createObjectNode()
                .put("id", id)
                .put("object", "turn")
                .put("status", "running")
                .toString();
    }

    static String pendingApprovals(List<RuntimeHitlHandler.PendingApproval> pendingApprovals) {
        ArrayNode data = MAPPER.createArrayNode();
        for (RuntimeHitlHandler.PendingApproval pending : pendingApprovals) {
            ObjectNode item = data.addObject();
            item.put("id", pending.id());
            item.put("tool", pending.request().toolName());
            item.put("arguments", pending.request().arguments());
            item.put("danger_level", pending.request().dangerLevel());
            item.put("risk_description", pending.request().riskDescription());
            item.put("suggestion", pending.request().suggestion());
        }
        ObjectNode response = MAPPER.createObjectNode();
        response.set("data", data);
        return response.toString();
    }

    static String approvalResolved(String id, ApprovalResult.Decision decision) {
        return MAPPER.createObjectNode()
                .put("id", id)
                .put("status", "resolved")
                .put("decision", decision.name())
                .toString();
    }
}
