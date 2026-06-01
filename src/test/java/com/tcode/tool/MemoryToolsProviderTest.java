package com.tcode.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryToolsProviderTest {

    @Test
    void registersSaveMemoryWithScopedSaver() {
        AtomicReference<BiConsumer<String, String>> saver = new AtomicReference<>();
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new MemoryToolsProvider(saver::get));
        List<String> saved = new ArrayList<>();
        saver.set((fact, scope) -> saved.add(scope + ":" + fact));

        String result = registry.executeTool("save_memory", "{\"fact\":\"prefer concise answers\",\"scope\":\"global\"}");

        assertEquals(List.of("global:prefer concise answers"), saved);
        assertTrue(result.contains("global"));
        assertTrue(result.contains("prefer concise answers"));
    }
}
