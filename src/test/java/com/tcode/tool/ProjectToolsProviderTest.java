package com.tcode.tool;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectToolsProviderTest {

    @Test
    void delegatesProjectCreationArguments() {
        AtomicReference<Map<String, String>> call = new AtomicReference<>();
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new ProjectToolsProvider(args -> {
            call.set(args);
            return "project-ok";
        }));

        assertEquals("project-ok", registry.executeTool("create_project", "{\"name\":\"demo\",\"type\":\"node\"}"));
        assertEquals("demo", call.get().get("name"));
        assertEquals("node", call.get().get("type"));
    }
}
