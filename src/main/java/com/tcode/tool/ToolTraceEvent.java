package com.tcode.tool;

import java.time.Instant;

public record ToolTraceEvent(
        Instant timestamp,
        String toolName,
        String argumentsJson,
        ToolCallStatus status,
        ToolErrorCode errorCode,
        boolean retryable,
        long elapsedMillis,
        int attempts,
        String resultPreview
) {
    public static ToolTraceEvent of(String toolName, String argumentsJson, ToolOutput output, int attempts) {
        ToolOutput safeOutput = output == null ? ToolOutput.text("") : output;
        return new ToolTraceEvent(
                Instant.now(),
                toolName,
                argumentsJson,
                safeOutput.status(),
                safeOutput.errorCode(),
                safeOutput.retryable(),
                safeOutput.elapsedMillis(),
                Math.max(attempts, 1),
                preview(safeOutput.text(), 300)
        );
    }

    private static String preview(String text, int limit) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }
}
