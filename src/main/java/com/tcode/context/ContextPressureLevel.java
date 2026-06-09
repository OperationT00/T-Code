package com.tcode.context;

public enum ContextPressureLevel {
    NORMAL,
    CONSERVE,
    COMPACT,
    CRITICAL;

    public static ContextPressureLevel fromUsage(int estimatedTokens, int maxContextWindow) {
        if (maxContextWindow <= 0) {
            return NORMAL;
        }
        double ratio = estimatedTokens / (double) maxContextWindow;
        if (ratio >= 0.95) {
            return CRITICAL;
        }
        if (ratio >= 0.85) {
            return COMPACT;
        }
        if (ratio >= 0.70) {
            return CONSERVE;
        }
        return NORMAL;
    }
}
