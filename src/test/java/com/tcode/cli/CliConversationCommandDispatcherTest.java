package com.tcode.cli;

import com.tcode.agent.Agent;
import com.tcode.context.ContextEvent;
import com.tcode.llm.GLMClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Test
    void handlesManualContextCompactWithFocus() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordingAgent agent = new RecordingAgent();
        CliConversationCommandDispatcher.Context context =
                new CliConversationCommandDispatcher.Context(new PrintStream(output), agent, null);

        assertTrue(CliConversationCommandDispatcher.dispatch(
                new CliCommandParser.ParsedCommand(
                        CliCommandParser.CommandType.CONTEXT_COMPACT,
                        "keep failed tests"),
                context));

        assertTrue(agent.compactCalled);
        assertTrue(agent.focus.equals("keep failed tests"));
    }

    @Test
    void handlesReadOnlyContextEventCommands() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordingAgent agent = new RecordingAgent();
        ContextEvent event = ContextEvent.tool("turn-1", "read_file", "full raw content", Map.of("file", "Agent.java"));
        agent.events = List.of(event);
        CliConversationCommandDispatcher.Context context =
                new CliConversationCommandDispatcher.Context(new PrintStream(output), agent, null);

        assertTrue(CliConversationCommandDispatcher.dispatch(
                new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.CONTEXT_EVENTS, null), context));
        assertTrue(CliConversationCommandDispatcher.dispatch(
                new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.CONTEXT_RECALL, "raw"), context));
        assertTrue(CliConversationCommandDispatcher.dispatch(
                new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.CONTEXT_SHOW, event.id()), context));

        String text = output.toString();
        assertTrue(text.contains(event.id()));
        assertTrue(text.contains("read_file"));
        assertTrue(text.contains("full raw content"));
    }

    @Test
    void handlesExplicitContextInjection() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordingAgent agent = new RecordingAgent();
        agent.injectResult = true;
        CliConversationCommandDispatcher.Context context =
                new CliConversationCommandDispatcher.Context(new PrintStream(output), agent, null);

        assertTrue(CliConversationCommandDispatcher.dispatch(
                new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.CONTEXT_INJECT, "ctx_123"),
                context));

        assertTrue(agent.injectCalled);
        assertTrue(agent.injectedId.equals("ctx_123"));
        assertTrue(output.toString().contains("ctx_123"));
    }

    private static final class RecordingAgent extends Agent {
        private boolean compactCalled;
        private String focus;
        private List<ContextEvent> events = List.of();
        private boolean injectCalled;
        private boolean injectResult;
        private String injectedId;

        private RecordingAgent() {
            super(new GLMClient("test-key"));
        }

        @Override
        public boolean compactContext(String focus) {
            this.compactCalled = true;
            this.focus = focus;
            return true;
        }

        @Override
        public List<ContextEvent> recentContextEvents(int limit) {
            return events;
        }

        @Override
        public List<ContextEvent> searchContextEvents(String keyword, int limit) {
            return events.stream()
                    .filter(event -> event.content().contains(keyword))
                    .toList();
        }

        @Override
        public Optional<ContextEvent> findContextEvent(String id) {
            return events.stream()
                    .filter(event -> event.id().equals(id))
                    .findFirst();
        }

        @Override
        public boolean injectContextEvent(String id) {
            this.injectCalled = true;
            this.injectedId = id;
            return injectResult;
        }
    }
}
