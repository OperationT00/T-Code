package com.tcode.context;

import com.tcode.llm.LlmClient;
import com.tcode.memory.TokenBudget;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owns the LLM message history for the current conversation context.
 */
public final class ContextManager {
    private final List<LlmClient.Message> messages = new ArrayList<>();
    private final ConversationHistoryCompactor compactor;
    private final ToolResultSummarizers toolResultSummarizers;
    private final ContextEventStore eventStore;
    private ContextProfile contextProfile;
    private int summarizedToolResults;
    private int summarizedToolResultOriginalChars;
    private int summarizedToolResultStoredChars;
    private int historyCompactions;
    private int turnSequence;
    private String currentTurnId = "turn-0";

    public ContextManager(LlmClient llmClient, ContextProfile contextProfile) {
        this(llmClient, contextProfile, ContextEventStore.noop());
    }

    public ContextManager(LlmClient llmClient, ContextProfile contextProfile, ContextEventStore eventStore) {
        this.contextProfile = contextProfile == null ? ContextProfile.from(llmClient) : contextProfile;
        this.compactor = new ConversationHistoryCompactor(llmClient);
        this.toolResultSummarizers = ToolResultSummarizers.defaults();
        this.eventStore = eventStore == null ? ContextEventStore.noop() : eventStore;
    }

    public void setLlmClient(LlmClient llmClient) {
        compactor.setLlmClient(llmClient);
    }

    public void setContextProfile(ContextProfile contextProfile) {
        if (contextProfile != null) {
            this.contextProfile = contextProfile;
        }
    }

    public void setSystemPrompt(String prompt) {
        LlmClient.Message system = LlmClient.Message.system(prompt == null ? "" : prompt);
        if (!messages.isEmpty() && "system".equals(messages.get(0).role())) {
            messages.set(0, system);
        } else {
            messages.add(0, system);
        }
    }

    public void addUserMessage(String content) {
        currentTurnId = "turn-" + (++turnSequence);
        recordEvent(ContextEvent.user(currentTurnId, content, Map.of()));
        addMessage(LlmClient.Message.user(content));
    }

    public void addUserMessage(LlmClient.Message message) {
        currentTurnId = "turn-" + (++turnSequence);
        recordEvent(ContextEvent.user(currentTurnId, message == null ? "" : message.content(),
                messageMetadata(message)));
        addMessage(message);
    }

    public void addAssistantMessage(String content) {
        recordEvent(ContextEvent.assistant(ensureTurnId(), content, Map.of()));
        addMessage(LlmClient.Message.assistant(content));
    }

    public void addAssistantMessage(LlmClient.Message message) {
        recordEvent(ContextEvent.assistant(ensureTurnId(), message == null ? "" : message.content(),
                messageMetadata(message)));
        addMessage(message);
    }

    public void addToolMessage(String toolCallId, String result) {
        addToolMessage(toolCallId, null, result);
    }

    public void addToolMessage(String toolCallId, String toolName, String result) {
        recordEvent(ContextEvent.tool(ensureTurnId(), toolName, result,
                Map.of("toolCallId", toolCallId == null ? "" : toolCallId)));
        String stored = toolResultSummarizers.summarize(
                toolName,
                result,
                ToolSummaryPolicy.forLevel(pressureLevel()));
        if (result != null && stored != null && stored.length() < result.length()) {
            summarizedToolResults++;
            summarizedToolResultOriginalChars += result.length();
            summarizedToolResultStoredChars += stored.length();
        }
        addMessage(LlmClient.Message.tool(toolCallId, stored));
    }

    public void addMessage(LlmClient.Message message) {
        if (message != null) {
            messages.add(message);
        }
    }

    public LlmClient.Message get(int index) {
        return messages.get(index);
    }

    public void set(int index, LlmClient.Message message) {
        messages.set(index, message);
    }

    public int size() {
        return messages.size();
    }

    public List<LlmClient.Message> mutableMessages() {
        return messages;
    }

    public List<LlmClient.Message> messages() {
        return new ArrayList<>(messages);
    }

    public boolean compactIfNeeded(PrintStream out) {
        boolean compacted = compactor.compactIfNeeded(messages, contextProfile.compressionTriggerTokens());
        if (compacted && out != null) {
            out.println("📎 上下文接近窗口上限，已把早期对话压缩为摘要后继续。");
        }
        if (compacted) {
            historyCompactions++;
            recordCompactionEvent("automatic");
        }
        return compacted;
    }

    public boolean compactNow(PrintStream out, String focus) {
        boolean compacted = compactor.compactNow(messages, focus);
        if (compacted && out != null) {
            out.println("Context compacted. Recent turns were preserved.");
        }
        if (!compacted && out != null) {
            out.println("Context is too short to compact. Add more conversation turns first.");
        }
        if (compacted) {
            historyCompactions++;
            recordCompactionEvent("manual");
        }
        return compacted;
    }

    public ContextPressureLevel pressureLevel() {
        return ContextPressureLevel.fromUsage(
                TokenBudget.estimateMessagesTokens(messages),
                contextProfile.maxContextWindow());
    }

    public void clearKeepingSystem() {
        LlmClient.Message system = !messages.isEmpty() && "system".equals(messages.get(0).role())
                ? messages.get(0)
                : null;
        messages.clear();
        if (system != null) {
            messages.add(system);
        }
    }

    public static String summarizeToolResult(String result) {
        return summarizeToolResult(null, result);
    }

    public static String summarizeToolResult(String toolName, String result) {
        return summarizeToolResult(toolName, result, ToolSummaryPolicy.forLevel(ContextPressureLevel.NORMAL));
    }

    public static String summarizeToolResult(String toolName, String result, ToolSummaryPolicy policy) {
        return ToolResultSummarizers.defaults().summarize(toolName, result, policy);
    }

    public int summarizedToolResults() {
        return summarizedToolResults;
    }

    public int summarizedToolResultOriginalChars() {
        return summarizedToolResultOriginalChars;
    }

    public int summarizedToolResultStoredChars() {
        return summarizedToolResultStoredChars;
    }

    public int historyCompactions() {
        return historyCompactions;
    }

    public List<ContextEvent> recentEvents(int limit) {
        return eventStore.recent(limit);
    }

    public List<ContextEvent> searchEvents(String keyword, int limit) {
        return eventStore.search(keyword, limit);
    }

    public Optional<ContextEvent> findEvent(String id) {
        return eventStore.findById(id);
    }

    public boolean injectEvent(String id) {
        Optional<ContextEvent> event = findEvent(id);
        if (event.isEmpty()) {
            return false;
        }
        ContextEvent value = event.get();
        ToolSummaryPolicy policy = ToolSummaryPolicy.forLevel(pressureLevel());
        String content = bounded(value.content(), policy.maxChars(), policy.edgeChars());
        addUserMessage("""
                [Recalled context event: %s]
                role: %s%s

                %s
                """.formatted(
                value.id(),
                value.role(),
                value.toolName().isBlank() ? "" : "\ntool: " + value.toolName(),
                content));
        return true;
    }

    private String ensureTurnId() {
        if (turnSequence == 0) {
            currentTurnId = "turn-" + (++turnSequence);
        }
        return currentTurnId;
    }

    private Map<String, String> messageMetadata(LlmClient.Message message) {
        if (message == null) {
            return Map.of();
        }
        int imageParts = message.imagePartCount();
        if (imageParts > 0) {
            return Map.of("imageParts", Integer.toString(imageParts));
        }
        return Map.of();
    }

    private void recordCompactionEvent(String mode) {
        recordEvent(ContextEvent.compaction(ensureTurnId(), compactionEventContent(), Map.of("mode", mode)));
    }

    private String compactionEventContent() {
        for (LlmClient.Message message : messages) {
            if ("user".equals(message.role())
                    && message.content() != null
                    && message.content().contains("压缩")) {
                return message.content();
            }
        }
        return "conversation history compacted";
    }

    private void recordEvent(ContextEvent event) {
        try {
            eventStore.append(event);
        } catch (RuntimeException ignored) {
            // Context event logging is observational; it must not block agent execution.
        }
    }

    private static String bounded(String content, int maxChars, int edgeChars) {
        String text = content == null ? "" : content;
        if (text.length() <= maxChars) {
            return text;
        }
        int edge = Math.max(1, Math.min(edgeChars, maxChars / 2));
        String head = text.substring(0, edge);
        String tail = text.substring(text.length() - edge);
        return """
                [Recalled event content summarized: original %d chars, omitted %d chars]
                --- head ---
                %s
                --- tail ---
                %s
                """.formatted(text.length(), text.length() - head.length() - tail.length(), head, tail);
    }
}
