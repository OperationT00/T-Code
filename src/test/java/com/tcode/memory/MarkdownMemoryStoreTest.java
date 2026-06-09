package com.tcode.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownMemoryStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void savesProjectAndGlobalFactsToMarkdownFiles() throws Exception {
        String oldMemoryDir = System.getProperty("tcode.memory.dir");
        System.setProperty("tcode.memory.dir", tempDir.resolve("global").toString());
        try {
            MarkdownMemoryStore store = new MarkdownMemoryStore(tempDir.resolve("project"));

            store.save("项目使用 Java 17", MemoryScope.PROJECT);
            store.save("默认用中文回答", MemoryScope.GLOBAL);

            assertTrue(Files.readString(store.projectFile()).contains("- 项目使用 Java 17"));
            assertTrue(Files.readString(store.globalFile()).contains("- 默认用中文回答"));
            assertEquals(2, store.listVisible().size());
        } finally {
            restoreProperty("tcode.memory.dir", oldMemoryDir);
        }
    }

    @Test
    void ignoresExactDuplicateFacts() {
        MarkdownMemoryStore store = new MarkdownMemoryStore(tempDir);

        store.save("项目使用 Java 17", MemoryScope.PROJECT);
        store.save("项目使用 Java 17", MemoryScope.PROJECT);

        assertEquals(1, store.listVisible().size());
    }

    @Test
    void searchesAndDeletesByStableLineId() {
        MarkdownMemoryStore store = new MarkdownMemoryStore(tempDir);
        store.save("项目使用 Java 17", MemoryScope.PROJECT);
        store.save("默认用中文回答", MemoryScope.PROJECT);

        assertEquals(1, store.search("Java", 10).size());
        assertTrue(store.delete("project:1"));

        assertFalse(store.listVisible().stream()
                .anyMatch(line -> line.content().contains("Java 17")));
        assertEquals("project:1", store.listVisible().get(0).id());
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
