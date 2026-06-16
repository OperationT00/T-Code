package com.tcode.tool;

import com.tcode.llm.LlmClient;

import java.util.List;

public record ToolOutput(String text,
                         List<LlmClient.ContentPart> imageParts,
                         ToolCallStatus status,
                         ToolErrorCode errorCode,
                         boolean retryable,
                         long elapsedMillis,
                         int attempts) {
    public ToolOutput(String text, List<LlmClient.ContentPart> imageParts) {
        this(text, imageParts, ToolCallStatus.SUCCEEDED, ToolErrorCode.NONE, false, 0, 1);
    }

    public ToolOutput {
        text = text == null ? "" : text;
        imageParts = imageParts == null ? List.of() : List.copyOf(imageParts);
        status = status == null ? ToolCallStatus.SUCCEEDED : status;
        errorCode = errorCode == null ? ToolErrorCode.NONE : errorCode;
        attempts = Math.max(attempts, 1);
    }

    public static ToolOutput text(String text) {
        return new ToolOutput(text, List.of());
    }

    public static ToolOutput failure(String text, ToolErrorCode errorCode, boolean retryable) {
        return new ToolOutput(text, List.of(), ToolCallStatus.FAILED, errorCode, retryable, 0, 1);
    }

    public static ToolOutput denied(String text, ToolErrorCode errorCode) {
        return new ToolOutput(text, List.of(), ToolCallStatus.DENIED, errorCode, false, 0, 1);
    }

    public static ToolOutput timedOut(String text) {
        return new ToolOutput(text, List.of(), ToolCallStatus.TIMED_OUT, ToolErrorCode.TIMEOUT, true, 0, 1);
    }

    public ToolOutput withTiming(long elapsedMillis) {
        return new ToolOutput(text, imageParts, status, errorCode, retryable, elapsedMillis, attempts);
    }

    public ToolOutput withAttempts(int attempts) {
        return new ToolOutput(text, imageParts, status, errorCode, retryable, elapsedMillis, attempts);
    }

    public boolean hasImageParts() {
        return !imageParts.isEmpty();
    }

    public boolean succeeded() {
        return status == ToolCallStatus.SUCCEEDED;
    }
}
