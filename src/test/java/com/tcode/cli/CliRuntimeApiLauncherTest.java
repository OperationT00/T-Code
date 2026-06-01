package com.tcode.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliRuntimeApiLauncherTest {
    @Test
    void recognizesHttpServeCommandOnly() {
        assertTrue(CliRuntimeApiLauncher.isServeCommand(new String[]{"serve", "--http"}));
        assertTrue(CliRuntimeApiLauncher.isServeCommand(new String[]{"SERVE", "--HTTP", "--port", "9090"}));
        assertFalse(CliRuntimeApiLauncher.isServeCommand(new String[]{"serve"}));
        assertFalse(CliRuntimeApiLauncher.isServeCommand(new String[]{"chat", "--http"}));
        assertFalse(CliRuntimeApiLauncher.isServeCommand(null));
    }

    @Test
    void parsesPortAndFallsBackForMissingOrInvalidValues() {
        assertEquals(9090, CliRuntimeApiLauncher.parsePort(new String[]{"serve", "--http", "--port", "9090"}, 8080));
        assertEquals(8080, CliRuntimeApiLauncher.parsePort(new String[]{"serve", "--http", "--port"}, 8080));
        assertEquals(8080, CliRuntimeApiLauncher.parsePort(new String[]{"serve", "--http", "--port", "bad"}, 8080));
        assertEquals(8080, CliRuntimeApiLauncher.parsePort(null, 8080));
    }
}
