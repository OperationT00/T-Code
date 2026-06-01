package com.tcode.cli;

import com.tcode.mcp.McpServerManager;
import com.tcode.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliMcpInfrastructureTest {

    @Test
    void initializesManagerExpandersAndShutdownHook(@TempDir Path tempDir) {
        Path home = tempDir.resolve("home");
        Path projectDir = tempDir.resolve("project");
        FakeMcpServerManager manager = new FakeMcpServerManager(projectDir);
        List<Thread> shutdownHooks = new ArrayList<>();

        CliMcpInfrastructure infrastructure = CliMcpInfrastructure.start(
                home, projectDir, manager, System.out, Duration.ofSeconds(3), shutdownHooks::add);

        assertTrue(manager.loaded);
        assertEquals(Duration.ofSeconds(3), manager.startupWait);
        assertNotNull(infrastructure.mentionExpander());
        assertNotNull(infrastructure.localPathMentionExpander());
        assertEquals(1, shutdownHooks.size());
        assertEquals("tcode-mcp-shutdown", shutdownHooks.get(0).getName());
        assertTrue(Files.exists(home.resolve(".tcode/mcp.json")));
    }

    private static final class FakeMcpServerManager extends McpServerManager {
        private boolean loaded;
        private Duration startupWait;

        private FakeMcpServerManager(Path projectDir) {
            super(new ToolRegistry(), projectDir);
        }

        @Override
        public void loadConfiguredServers() {
            loaded = true;
        }

        @Override
        public void startAll(PrintStream progressOut, Duration maxWait) {
            startupWait = maxWait;
        }
    }
}
