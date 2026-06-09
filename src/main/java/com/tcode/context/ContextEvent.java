package com.tcode.context;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ContextEvent(
        String id,
        String turnId,
        String role,
        String toolName,
        String content,
        Map<String, String> metadata,
        String createdAt
) {
    public ContextEvent {
        id = id == null || id.isBlank() ? "ctx_" + UUID.randomUUID() : id;
        turnId = turnId == null ? "" : turnId;
        role = role == null ? "" : role;
        toolName = toolName == null ? "" : toolName;
        content = content == null ? "" : content;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null || createdAt.isBlank() ? Instant.now().toString() : createdAt;
    }

    public static ContextEvent user(String turnId, String content, Map<String, String> metadata) {
        return of(turnId, "user", "", content, metadata);
    }

    public static ContextEvent assistant(String turnId, String content, Map<String, String> metadata) {
        return of(turnId, "assistant", "", content, metadata);
    }

    public static ContextEvent tool(String turnId, String toolName, String content, Map<String, String> metadata) {
        return of(turnId, "tool", toolName, content, metadata);
    }

    public static ContextEvent compaction(String turnId, String content, Map<String, String> metadata) {
        return of(turnId, "compaction", "", content, metadata);
    }

    private static ContextEvent of(
            String turnId,
            String role,
            String toolName,
            String content,
            Map<String, String> metadata
    ) {
        return new ContextEvent(
                "ctx_" + UUID.randomUUID(),
                turnId,
                role,
                toolName,
                content,
                metadata == null ? Map.of() : new LinkedHashMap<>(metadata),
                Instant.now().toString());
    }
}
