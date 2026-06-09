package com.tcode.context;

public record ToolSummaryPolicy(int maxChars, int edgeChars) {
    public static ToolSummaryPolicy forLevel(ContextPressureLevel level) {
        return switch (level == null ? ContextPressureLevel.NORMAL : level) {
            case NORMAL -> new ToolSummaryPolicy(4_000, 1_200);
            case CONSERVE -> new ToolSummaryPolicy(2_500, 800);
            case COMPACT -> new ToolSummaryPolicy(1_600, 500);
            case CRITICAL -> new ToolSummaryPolicy(800, 250);
        };
    }
}
