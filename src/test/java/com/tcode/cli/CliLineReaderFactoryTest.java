package com.tcode.cli;

import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliLineReaderFactoryTest {

    @Test
    void createsConfiguredInteractiveLineReader(@TempDir Path home) throws Exception {
        try (Terminal terminal = TerminalBuilder.builder()
                .dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .build()) {
            LineReader lineReader = CliLineReaderFactory.create(terminal, List::of, List::of, home);

            assertTrue(lineReader.getHistory() instanceof TCodeHistory);
            assertTrue(lineReader.isSet(LineReader.Option.BRACKETED_PASTE));
            assertTrue(lineReader.isSet(LineReader.Option.AUTO_LIST));
            assertTrue(lineReader.isSet(LineReader.Option.AUTO_MENU));
            assertTrue(lineReader.getWidgets().containsKey("tcode-slash-command-hint"));
            assertEquals(home.resolve(".tcode/history/input.history").toAbsolutePath().normalize(),
                    lineReader.getVariable(LineReader.HISTORY_FILE));
        }
    }
}
