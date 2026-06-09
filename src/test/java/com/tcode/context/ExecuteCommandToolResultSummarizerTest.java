package com.tcode.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecuteCommandToolResultSummarizerTest {

    @Test
    void keepsFailuresAndTailForCommandOutput() {
        String output = """
                command: mvn test -Pquick
                exitCode=1
                lots
                lots
                FAILURE: ContextManagerTest.shouldCompact
                expected true but was false
                %s
                final diagnostic line
                """.formatted("noise\n".repeat(2_000));

        String summarized = new ExecuteCommandToolResultSummarizer()
                .summarize("execute_command", output, ToolSummaryPolicy.forLevel(ContextPressureLevel.COMPACT));

        assertTrue(summarized.contains("[execute_command summarized]"));
        assertTrue(summarized.contains("command: mvn test -Pquick"));
        assertTrue(summarized.contains("exitCode=1"));
        assertTrue(summarized.contains("FAILURE: ContextManagerTest.shouldCompact"));
        assertTrue(summarized.contains("expected true but was false"));
        assertTrue(summarized.contains("--- tail ---"));
        assertTrue(summarized.contains("final diagnostic line"));
        assertFalse(summarized.contains("noise\nnoise\nnoise\nnoise\nnoise\nnoise"));
    }
}
