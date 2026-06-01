package com.tcode.cli;

import com.tcode.hitl.SwitchableHitlHandler;
import com.tcode.hitl.TerminalHitlHandler;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliControlCommandDispatcherTest {

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
}
