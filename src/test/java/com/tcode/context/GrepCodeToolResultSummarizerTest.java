package com.tcode.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrepCodeToolResultSummarizerTest {

    @Test
    void keepsPathsLineNumbersAndMatchedLines() {
        String output = """
                src/main/java/com/tcode/agent/Agent.java:72: private final ContextManager contextManager;
                src/main/java/com/tcode/agent/Agent.java:204: contextManager.addToolMessage(id, name, result);
                %s
                src/test/java/com/tcode/context/ContextManagerTest.java:18: ContextPressureLevel.NORMAL
                """.formatted("noise without line number\n".repeat(1_000));

        String summarized = new GrepCodeToolResultSummarizer()
                .summarize("grep_code", output, ToolSummaryPolicy.forLevel(ContextPressureLevel.COMPACT));

        assertTrue(summarized.contains("[grep_code summarized]"));
        assertTrue(summarized.contains("src/main/java/com/tcode/agent/Agent.java:72"));
        assertTrue(summarized.contains("contextManager.addToolMessage"));
        assertTrue(summarized.contains("src/test/java/com/tcode/context/ContextManagerTest.java:18"));
        assertFalse(summarized.contains("noise without line number\nnoise without line number"));
    }
}
