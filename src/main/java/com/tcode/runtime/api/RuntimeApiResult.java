package com.tcode.runtime.api;

public record RuntimeApiResult(int status, String contentType, String body) {
    private static final String JSON = "application/json; charset=utf-8";
    private static final String SSE = "text/event-stream; charset=utf-8";

    public RuntimeApiResult {
        contentType = contentType == null ? JSON : contentType;
        body = body == null ? "" : body;
    }

    public static RuntimeApiResult json(int status, String body) {
        return new RuntimeApiResult(status, JSON, body);
    }

    public static RuntimeApiResult sse(String body) {
        return new RuntimeApiResult(200, SSE, body);
    }
}
