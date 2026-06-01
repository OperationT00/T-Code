package com.tcode.runtime.api;

public record RuntimeApiRequest(String method, String path, String query, String body) {
    public RuntimeApiRequest {
        method = method == null ? "" : method;
        path = path == null ? "" : path;
        body = body == null ? "" : body;
    }
}
