package com.tcode.runtime.api;

import java.util.Map;

public final class RuntimeApiAuthPolicy {
    public static final String API_KEY_HEADER = "X-TCode-API-Key";
    private final String apiKey;

    public RuntimeApiAuthPolicy(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Runtime API requires TCODE_RUNTIME_API_KEY or -Dtcode.runtime.api.key");
        }
        this.apiKey = apiKey;
    }

    public boolean authorized(Map<String, String> headers) {
        if (headers == null) {
            return false;
        }
        return ("Bearer " + apiKey).equals(headers.get("Authorization"))
                || apiKey.equals(headers.get(API_KEY_HEADER));
    }

    public static String configuredApiKey() {
        String configured = System.getProperty("tcode.runtime.api.key");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("TCODE_RUNTIME_API_KEY");
        }
        return configured;
    }
}
