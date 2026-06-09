package com.tcode.cli;

import com.tcode.hitl.SwitchableHitlHandler;
import com.tcode.hitl.TerminalHitlHandler;
import com.tcode.llm.GLMClient;
import com.tcode.memory.MemoryManager;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliControlCommandDispatcherTest {
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
    void handlesHistoryClearAndHitlStatusRefresh() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SwitchableHitlHandler hitlHandler = new SwitchableHitlHandler(new TerminalHitlHandler(false));
        AtomicInteger statusRefreshes = new AtomicInteger();
        try (Terminal terminal = TerminalBuilder.builder()
                .dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), output)
                .build()) {
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .history(new TCodeHistory())
                    .build();
            lineReader.getHistory().add("hello");
            CliControlCommandDispatcher.Context context = new CliControlCommandDispatcher.Context(
                    new PrintStream(output), lineReader, hitlHandler, null, null, null,
                    null, null, null, null, null, null, statusRefreshes::incrementAndGet);

            assertTrue(CliControlCommandDispatcher.dispatch(
                    new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.HISTORY_CLEAR, null), context));
            assertTrue(lineReader.getHistory().isEmpty());

            assertTrue(CliControlCommandDispatcher.dispatch(
                    new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.SWITCH_HITL, "on"), context));
            assertTrue(hitlHandler.isEnabled());
            assertEquals(1, statusRefreshes.get());
        }
    }

    @Test
    void handlesBlankMemorySearchWithoutRequiringMemoryManager() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliControlCommandDispatcher.Context context = new CliControlCommandDispatcher.Context(
                new PrintStream(output), null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertTrue(CliControlCommandDispatcher.dispatch(
                new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.MEMORY_SEARCH, " "), context));
        assertFalse(output.toString().isBlank());
    }

    @Test
    void memoryListShowsMarkdownFilesAndLineIds() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MemoryManager memoryManager = new MemoryManager(new GLMClient("test-key"));
        memoryManager.setProjectPath(tempDir.resolve("project").toString());
        memoryManager.storeFact("Project uses Java 17");
        memoryManager.storeFact("Prefer concise answers", "global");
        CliControlCommandDispatcher.Context context = new CliControlCommandDispatcher.Context(
                new PrintStream(output), null, null, null, null, null,
                null, null, null, null, memoryManager, null, null);

        assertTrue(CliControlCommandDispatcher.dispatch(
                new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.MEMORY_LIST, null), context));

        String rendered = output.toString();
        assertTrue(rendered.contains("project.md"), rendered);
        assertTrue(rendered.contains("user.md"), rendered);
        assertTrue(rendered.contains("project:1"), rendered);
        assertTrue(rendered.contains("global:1"), rendered);
        assertTrue(rendered.contains("Project uses Java 17"), rendered);
    }

    @Test
    void memoryOpenEnsuresMarkdownFileAndPrintsPath() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MemoryManager memoryManager = new MemoryManager(new GLMClient("test-key"));
        memoryManager.setProjectPath(tempDir.resolve("project").toString());
        CliControlCommandDispatcher.Context context = new CliControlCommandDispatcher.Context(
                new PrintStream(output), null, null, null, null, null,
                null, null, null, null, memoryManager, null, null);

        assertTrue(CliControlCommandDispatcher.dispatch(
                new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.MEMORY_OPEN, "project"), context));

        String rendered = output.toString();
        assertTrue(rendered.contains("project.md"), rendered);
        assertTrue(Files.exists(memoryManager.getMarkdownStore().projectFile()));
    }
}
