package com.tcode.tui;

import com.tcode.llm.GLMClient;
import com.tcode.memory.MemoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiSessionControllerTest {

    @TempDir
    Path tempDir;
    private String oldMemoryDir;

    @BeforeEach
    void isolateMemoryDir() {
        oldMemoryDir = System.getProperty("tcode.memory.dir");
        System.setProperty("tcode.memory.dir", tempDir.resolve("global-memory").toString());
    }

    @AfterEach
    void restoreMemoryDir() {
        if (oldMemoryDir == null) {
            System.clearProperty("tcode.memory.dir");
        } else {
            System.setProperty("tcode.memory.dir", oldMemoryDir);
        }
    }

    @Test
    void memoryOpenCreatesProjectMarkdownFile() {
        Path projectRoot = tempDir.resolve("project");
        MemoryManager memoryManager = new MemoryManager(new GLMClient("test-key"));
        memoryManager.setProjectPath(projectRoot.toString());

        String output = TuiSessionController.formatMemoryOpenResult(memoryManager, "project");

        assertTrue(Files.exists(projectRoot.resolve(".tcode/memory/project.md")));
        assertTrue(output.contains("Memory file (project):"));
        assertTrue(output.contains("project.md"));
    }

    @Test
    void memoryOpenAcceptsGlobalAliasForUserMarkdownFile() {
        MemoryManager memoryManager = new MemoryManager(new GLMClient("test-key"));

        String output = TuiSessionController.formatMemoryOpenResult(memoryManager, "global");

        assertTrue(Files.exists(tempDir.resolve("global-memory/user.md")));
        assertTrue(output.contains("Memory file (global):"));
        assertTrue(output.contains("user.md"));
    }
}
