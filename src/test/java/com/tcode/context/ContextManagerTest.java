package com.tcode.context;

import com.tcode.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextManagerTest {

    @Test
    void pressureLevelChangesWithEstimatedTokens() {
        ContextProfile profile = ContextProfile.custom(10_000, 4_000);

        assertEquals(ContextPressureLevel.NORMAL,
                ContextPressureLevel.fromUsage(6_999, profile.maxContextWindow()));
        assertEquals(ContextPressureLevel.CONSERVE,
                ContextPressureLevel.fromUsage(7_000, profile.maxContextWindow()));
        assertEquals(ContextPressureLevel.COMPACT,
                ContextPressureLevel.fromUsage(8_500, profile.maxContextWindow()));
        assertEquals(ContextPressureLevel.CRITICAL,
                ContextPressureLevel.fromUsage(9_500, profile.maxContextWindow()));
    }

    @Test
    void toolSummaryPolicyTightensUnderPressure() {
        assertEquals(4_000, ToolSummaryPolicy.forLevel(ContextPressureLevel.NORMAL).maxChars());
        assertEquals(1_200, ToolSummaryPolicy.forLevel(ContextPressureLevel.NORMAL).edgeChars());
        assertEquals(2_500, ToolSummaryPolicy.forLevel(ContextPressureLevel.CONSERVE).maxChars());
        assertEquals(800, ToolSummaryPolicy.forLevel(ContextPressureLevel.CONSERVE).edgeChars());
        assertEquals(1_600, ToolSummaryPolicy.forLevel(ContextPressureLevel.COMPACT).maxChars());
        assertEquals(500, ToolSummaryPolicy.forLevel(ContextPressureLevel.COMPACT).edgeChars());
        assertEquals(800, ToolSummaryPolicy.forLevel(ContextPressureLevel.CRITICAL).maxChars());
        assertEquals(250, ToolSummaryPolicy.forLevel(ContextPressureLevel.CRITICAL).edgeChars());
    }

    @Test
    void startsWithSystemPromptAndAppendsUserAssistantAndToolMessages() {
        ContextManager manager = new ContextManager(null, ContextProfile.custom(8000, 4000));
        manager.setSystemPrompt("system");
        manager.addUserMessage(LlmClient.Message.user("hello"));
        manager.addAssistantMessage(LlmClient.Message.assistant("hi"));
        manager.addToolMessage("tool-id", "result");

        List<LlmClient.Message> messages = manager.messages();

        assertEquals("system", messages.get(0).role());
        assertEquals("user", messages.get(1).role());
        assertEquals("assistant", messages.get(2).role());
        assertEquals("tool", messages.get(3).role());
    }

    @Test
    void replacesExistingSystemPromptWithoutDroppingMessages() {
        ContextManager manager = new ContextManager(null, ContextProfile.custom(8000, 4000));
        manager.setSystemPrompt("old");
        manager.addUserMessage("hello");

        manager.setSystemPrompt("new");

        List<LlmClient.Message> messages = manager.messages();
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("new", messages.get(0).content());
        assertEquals("hello", messages.get(1).content());
    }

    @Test
    void clearKeepingSystemPreservesOnlySystemPrompt() {
        ContextManager manager = new ContextManager(null, ContextProfile.custom(8000, 4000));
        manager.setSystemPrompt("system");
        manager.addUserMessage("hello");
        manager.addAssistantMessage("hi");

        manager.clearKeepingSystem();

        List<LlmClient.Message> messages = manager.messages();
        assertEquals(1, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("system", messages.get(0).content());
    }

    @Test
    void summarizesOversizedToolResultsBeforeAddingToContext() {
        ContextManager manager = new ContextManager(null, ContextProfile.custom(8000, 4000));
        String longResult = "A".repeat(5000) + "MIDDLE" + "Z".repeat(5000);

        manager.addToolMessage("tool-id", longResult);

        String stored = manager.messages().get(0).content();
        assertTrue(stored.length() < longResult.length() / 2);
        assertTrue(stored.contains("Tool result summarized"));
        assertTrue(stored.contains("A".repeat(80)));
        assertTrue(stored.contains("Z".repeat(80)));
        assertFalse(stored.contains("MIDDLE"));
    }

    @Test
    void tracksToolResultSummaryStats() {
        ContextManager manager = new ContextManager(null, ContextProfile.custom(8000, 4000));

        manager.addToolMessage("short", "short result");
        manager.addToolMessage("long", "A".repeat(10_000));

        assertEquals(1, manager.summarizedToolResults());
        assertTrue(manager.summarizedToolResultOriginalChars() > manager.summarizedToolResultStoredChars());
        assertEquals(0, manager.historyCompactions());
    }

    @Test
    void summarizesReadFileResultsWithFileContext() {
        String longRead = """
                读取 1 个文件
                └ src/main/java/App.java
                line 1
                %s
                line 999
                """.formatted("body\n".repeat(2_000));

        String summary = ContextManager.summarizeToolResult("read_file", longRead);

        assertTrue(summary.contains("[read_file summarized]"));
        assertTrue(summary.contains("Tool: read_file"));
        assertTrue(summary.contains("src/main/java/App.java"));
        assertTrue(summary.contains("line 1"));
        assertTrue(summary.contains("line 999"));
    }

    @Test
    void summarizesCommandResultsWithCommandContext() {
        String longCommand = """
                exitCode=0
                command: mvn test
                first line
                %s
                BUILD SUCCESS
                """.formatted("log\n".repeat(2_000));

        String summary = ContextManager.summarizeToolResult("execute_command", longCommand);

        assertTrue(summary.contains("Tool: execute_command"));
        assertTrue(summary.contains("exitCode=0"));
        assertTrue(summary.contains("command: mvn test"));
        assertTrue(summary.contains("BUILD SUCCESS"));
    }

    @Test
    void recordsRawToolResultWhileActiveContextKeepsSummary() {
        RecordingContextEventStore store = new RecordingContextEventStore();
        ContextManager manager = new ContextManager(null, ContextProfile.custom(8000, 4000), store);
        String raw = "A".repeat(5000) + "RAW_MIDDLE" + "Z".repeat(5000);

        manager.addToolMessage("call-1", "read_file", raw);

        String active = manager.messages().get(0).content();
        assertFalse(active.contains("RAW_MIDDLE"));
        assertEquals(1, store.events.size());
        assertEquals("tool", store.events.get(0).role());
        assertEquals("read_file", store.events.get(0).toolName());
        assertEquals(raw, store.events.get(0).content());
        assertEquals("call-1", store.events.get(0).metadata().get("toolCallId"));
    }

    @Test
    void recordsUserAndAssistantEvents() {
        RecordingContextEventStore store = new RecordingContextEventStore();
        ContextManager manager = new ContextManager(null, ContextProfile.custom(8000, 4000), store);

        manager.addUserMessage("hello");
        manager.addAssistantMessage("hi");

        assertEquals(2, store.events.size());
        assertEquals("user", store.events.get(0).role());
        assertEquals("hello", store.events.get(0).content());
        assertEquals("assistant", store.events.get(1).role());
        assertEquals("hi", store.events.get(1).content());
        assertEquals(store.events.get(0).turnId(), store.events.get(1).turnId());
    }

    @Test
    void injectsContextEventIntoActiveContextByIdWithBoundedContent() {
        RecordingContextEventStore store = new RecordingContextEventStore();
        ContextEvent event = ContextEvent.tool(
                "turn-1",
                "execute_command",
                "A".repeat(5000) + "MIDDLE_SHOULD_BE_BOUNDED" + "Z".repeat(5000),
                java.util.Map.of());
        store.append(event);
        ContextManager manager = new ContextManager(null, ContextProfile.custom(20_000, 10_000), store);

        boolean injected = manager.injectEvent(event.id());

        assertTrue(injected);
        String injectedContent = manager.messages().get(0).content();
        assertTrue(injectedContent.contains("[Recalled context event: " + event.id() + "]"));
        assertTrue(injectedContent.length() < event.content().length());
        assertFalse(injectedContent.contains("MIDDLE_SHOULD_BE_BOUNDED"));
    }

    @Test
    void injectEventReturnsFalseWhenEventDoesNotExist() {
        RecordingContextEventStore store = new RecordingContextEventStore();
        ContextManager manager = new ContextManager(null, ContextProfile.custom(8000, 4000), store);

        assertFalse(manager.injectEvent("missing"));
        assertTrue(manager.messages().isEmpty());
    }

    private static final class RecordingContextEventStore implements ContextEventStore {
        private final List<ContextEvent> events = new ArrayList<>();

        @Override
        public void append(ContextEvent event) {
            events.add(event);
        }

        @Override
        public List<ContextEvent> search(String keyword, int limit) {
            return events.stream()
                    .filter(event -> event.content().contains(keyword))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<ContextEvent> findById(String id) {
            return events.stream().filter(event -> event.id().equals(id)).findFirst();
        }

        @Override
        public List<ContextEvent> recent(int limit) {
            return events;
        }
    }
}
