package com.tcode.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileSearchToolsProviderTest {

    @Test
    void delegatesGlobAndGrepArguments() {
        List<Map<String, String>> calls = new ArrayList<>();
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new FileSearchToolsProvider(
                args -> {
                    calls.add(args);
                    return "glob-ok";
                },
                args -> {
                    calls.add(args);
                    return "grep-ok";
                }
        ));

        assertEquals("glob-ok", registry.executeTool("glob_files", "{\"pattern\":\"**/*.java\"}"));
        assertEquals("grep-ok", registry.executeTool("grep_code", "{\"pattern\":\"ToolProvider\"}"));
        assertEquals("**/*.java", calls.get(0).get("pattern"));
        assertEquals("ToolProvider", calls.get(1).get("pattern"));
    }
}
