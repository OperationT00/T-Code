package com.tcode.cli;

import com.tcode.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliModelCommandDispatcherTest {

    @Test
    void handlesModelStatusWithoutChangingClient() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LlmClient currentClient = new StubLlmClient();
        CliModelCommandDispatcher.Context context = new CliModelCommandDispatcher.Context(
                new PrintStream(output), null, () -> currentClient, null, null);

        CliModelCommandDispatcher.Result result = CliModelCommandDispatcher.dispatch(
                new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.SWITCH_MODEL, null), context);

        assertTrue(result.handled());
        assertSame(currentClient, result.client());
        assertFalse(output.toString().isBlank());
    }

    private static final class StubLlmClient implements LlmClient {
        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getModelName() {
            return "stub-model";
        }

        @Override
        public String getProviderName() {
            return "stub";
        }
    }
}
