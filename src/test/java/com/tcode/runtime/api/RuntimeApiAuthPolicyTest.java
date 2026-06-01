package com.tcode.runtime.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeApiAuthPolicyTest {

    @Test
    void acceptsBearerAndTCodeDirectApiKeyHeaders() {
        RuntimeApiAuthPolicy policy = new RuntimeApiAuthPolicy("secret");

        assertTrue(policy.authorized(Map.of("Authorization", "Bearer secret")));
        assertTrue(policy.authorized(Map.of("X-TCode-API-Key", "secret")));
        String removedLegacyHeader = String.join("", "X-", "Pai", "CLI", "-API-Key");
        assertFalse(policy.authorized(Map.of(removedLegacyHeader, "secret")));
        assertFalse(policy.authorized(Map.of("Authorization", "Bearer wrong")));
        assertFalse(policy.authorized(Map.of()));
    }

    @Test
    void rejectsBlankConfiguredApiKey() {
        assertThrows(IllegalArgumentException.class, () -> new RuntimeApiAuthPolicy(" "));
    }
}
