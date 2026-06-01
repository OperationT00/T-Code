package com.tcode.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliConversationCommandDispatcherTest {

    @Test
    void handlesCancelWithoutConversationDependencies() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliConversationCommandDispatcher.Context context =
                new CliConversationCommandDispatcher.Context(new PrintStream(output), null, null);

        assertTrue(CliConversationCommandDispatcher.dispatch(
                new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.CANCEL, null), context));
        assertFalse(output.toString().isBlank());
    }
}
