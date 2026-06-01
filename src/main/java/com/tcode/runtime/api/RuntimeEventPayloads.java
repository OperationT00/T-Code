package com.tcode.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class RuntimeEventPayloads {
    public static final int SCHEMA_VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RuntimeEventPayloads() {
    }

    public static String threadCreated(String threadId) {
        return payload().put("thread_id", threadId).toString();
    }

    public static String statusUpdated(String phase) {
        return payload().put("phase", phase).toString();
    }

    public static String turnStarted(String turnId, String input) {
        return payload().put("turn_id", turnId).put("input", input).toString();
    }

    public static String messageDelta(String turnId, String content) {
        return payload().put("turn_id", turnId).put("content", content).toString();
    }

    public static String turnCompleted(String turnId) {
        return payload().put("turn_id", turnId).put("status", "completed").toString();
    }

    public static String turnFailed(String turnId, String error) {
        return payload().put("turn_id", turnId).put("error", error).toString();
    }

    public static String toolStarted(String name, String argumentsJson) {
        return payload().put("name", name).set("arguments", jsonObject(argumentsJson)).toString();
    }

    public static String toolCompleted(String name, String result) {
        return payload().put("name", name).put("result", result).toString();
    }

    public static String hitlRequested(String tool, String argumentsJson) {
        return payload().put("tool", tool).set("arguments", jsonObject(argumentsJson)).toString();
    }

    public static String hitlResolved(String tool, String decision) {
        return payload().put("tool", tool).put("decision", decision).toString();
    }

    private static ObjectNode payload() {
        return MAPPER.createObjectNode().put("schema_version", SCHEMA_VERSION);
    }

    private static JsonNode jsonObject(String value) {
        if (value == null || value.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            JsonNode node = MAPPER.readTree(value);
            return node != null && node.isObject() ? node : MAPPER.createObjectNode();
        } catch (Exception ignored) {
            return MAPPER.createObjectNode();
        }
    }
}
