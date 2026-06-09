package com.tcode.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultSummarizersTest {

    @Test
    void fallsBackToHeadTailSummarizerForUnknownTools() {
        String result = "A".repeat(2_500) + "MIDDLE" + "Z".repeat(2_500);

        String summarized = ToolResultSummarizers.defaults()
                .summarize("unknown_tool", result, ToolSummaryPolicy.forLevel(ContextPressureLevel.NORMAL));

        assertTrue(summarized.contains("Tool result summarized"));
        assertTrue(summarized.contains("Tool: unknown_tool"));
        assertTrue(summarized.contains("--- head ---"));
        assertTrue(summarized.contains("--- tail ---"));
        assertTrue(summarized.contains("A".repeat(80)));
        assertTrue(summarized.contains("Z".repeat(80)));
    }
}
