package com.tcode.memory;

import com.tcode.llm.GLMClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryManagerTest {

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
    void conversationMessagesDoNotEnterMemoryManager() {
        MemoryManager memoryManager = new MemoryManager(new GLMClient("test-key"));
        memoryManager.setProjectPath(tempDir.resolve("project").toString());

        memoryManager.addUserMessage("user task");
        memoryManager.addAssistantMessage("assistant result");
        memoryManager.addToolResult("read_file", "file content");

        assertTrue(memoryManager.listLongTerm().isEmpty());
        assertTrue(memoryManager.buildMemoryContext().isBlank());
    }

    @Test
    void shouldClearLongTermMemoryOnlyWhenExplicitlyRequested() {
        MemoryManager memoryManager = new MemoryManager(new GLMClient("test-key"));
        memoryManager.setProjectPath(tempDir.resolve("project").toString());

        memoryManager.storeFact("User prefers Chinese replies", "global");
        memoryManager.storeFact("Project uses Java 17");
        assertEquals(2, memoryManager.listLongTerm().size());

        memoryManager.clearLongTerm();

        assertTrue(memoryManager.listLongTerm().isEmpty());
    }

    @Test
    void shouldStoreProjectScopedFactsByDefault() throws Exception {
        Path projectRoot = tempDir.resolve("current");
        MemoryManager memoryManager = new MemoryManager(new GLMClient("test-key"));
        memoryManager.setProjectPath(projectRoot.toString());

        memoryManager.storeFact("Project uses Java 17");
        memoryManager.storeFact("Prefer concise answers", "global");

        MemoryEntry projectEntry = memoryManager.searchLongTerm("Java", 5).get(0);
        assertEquals("project", projectEntry.getMetadata().get("scope"));
        assertTrue(projectEntry.getMetadata().get("project").endsWith("/current"));
        assertEquals("global", memoryManager.searchLongTerm("concise", 5).get(0).getMetadata().get("scope"));
        assertTrue(Files.readString(projectRoot.resolve(".tcode/memory/project.md")).contains("- Project uses Java 17"));
    }

    @Test
    void shouldSearchOnlyCurrentProjectAndGlobalFacts() {
        Path currentProject = tempDir.resolve("current");
        Path otherProject = tempDir.resolve("other");
        MemoryManager current = new MemoryManager(new GLMClient("test-key"));
        current.setProjectPath(currentProject.toString());
        current.storeFact("Current project uses Java 17");
        current.storeFact("Global preference uses concise answers", "global");

        MemoryManager other = new MemoryManager(new GLMClient("test-key"));
        other.setProjectPath(otherProject.toString());

        List<MemoryEntry> currentResults = current.searchLongTerm("Java", 10);
        List<MemoryEntry> otherResults = other.searchLongTerm("Java", 10);

        assertEquals(1, currentResults.size());
        assertTrue(otherResults.isEmpty());
        assertEquals(1, other.searchLongTerm("concise", 10).size());
    }

    @Test
    void shouldBuildMarkdownMemoryContext() {
        MemoryManager memoryManager = new MemoryManager(new GLMClient("test-key"));
        memoryManager.setProjectPath(tempDir.resolve("project").toString());
        memoryManager.storeFact("Project uses Maven");

        String context = memoryManager.buildMemoryContext();

        assertTrue(context.contains("## Long-term Memory"));
        assertTrue(context.contains("- [project] Project uses Maven"));
    }

    @Test
    void compressionTriggerRatioAppliesToAllModelsUniformly() {
        MemoryManager memoryManager = new MemoryManager(new GLMClient("test-key"));

        assertEquals(0.90, memoryManager.getContextProfile().compressionTriggerRatio(), 0.001);
        assertEquals(200000, memoryManager.getTokenBudget().getContextWindow());
        assertEquals(180000, memoryManager.getContextProfile().compressionTriggerTokens());
    }
}
