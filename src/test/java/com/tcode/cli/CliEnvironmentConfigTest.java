package com.tcode.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliEnvironmentConfigTest {
    @Test
    void expandsHomePrefixForLogDirectory() {
        String oldHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", Path.of("tmp", "t-code-home").toString());

            assertEquals(System.getProperty("user.home"),
                    CliEnvironmentConfig.expandHome("~"));
            assertEquals(Path.of(System.getProperty("user.home"), "logs").toString(),
                    CliEnvironmentConfig.expandHome("~/logs"));
            assertEquals("relative/logs", CliEnvironmentConfig.expandHome("relative/logs"));
        } finally {
            restoreProperty("user.home", oldHome);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
