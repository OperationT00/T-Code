package com.tcode.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadFileToolResultSummarizerTest {

    @Test
    void keepsFilePathAndStructuralLines() {
        String output = """
                读取 1 个文件
                  └ src/main/java/com/tcode/agent/Agent.java
                package com.tcode.agent;
                import com.tcode.context.ContextManager;
                public class Agent {
                    private final ContextManager contextManager;
                    public String getContextStatus() {
                        return "ok";
                    }
                }
                %s
                tail marker
                """.formatted("noise body\n".repeat(2_000));

        String summarized = new ReadFileToolResultSummarizer()
                .summarize("read_file", output, ToolSummaryPolicy.forLevel(ContextPressureLevel.COMPACT));

        assertTrue(summarized.contains("[read_file summarized]"));
        assertTrue(summarized.contains("src/main/java/com/tcode/agent/Agent.java"));
        assertTrue(summarized.contains("package com.tcode.agent;"));
        assertTrue(summarized.contains("import com.tcode.context.ContextManager;"));
        assertTrue(summarized.contains("public class Agent"));
        assertTrue(summarized.contains("public String getContextStatus()"));
        assertTrue(summarized.contains("tail marker"));
        assertFalse(summarized.contains("noise body\nnoise body\nnoise body"));
    }
}
