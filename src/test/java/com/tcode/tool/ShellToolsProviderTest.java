package com.tcode.tool;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShellToolsProviderTest {

    @Test
    void delegatesCommandExecution() {
        AtomicReference<String> command = new AtomicReference<>();
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new ShellToolsProvider(value -> {
            command.set(value);
            return "shell-ok";
        }));

        assertEquals("shell-ok", registry.executeTool("execute_command", "{\"command\":\"pwd\"}"));
        assertEquals("pwd", command.get());
    }
}
