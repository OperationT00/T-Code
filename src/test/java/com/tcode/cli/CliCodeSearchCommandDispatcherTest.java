package com.tcode.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliCodeSearchCommandDispatcherTest {

    @Test
    void handlesBlankSearchWithoutOpeningRetriever() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliCodeSearchCommandDispatcher.Context context =
                new CliCodeSearchCommandDispatcher.Context(new PrintStream(output), null, null);

        assertTrue(CliCodeSearchCommandDispatcher.dispatch(
                new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.SEARCH_CODE, " "), context));
        assertFalse(output.toString().isBlank());
    }
}
