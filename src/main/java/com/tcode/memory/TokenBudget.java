package com.tcode.memory;

import com.tcode.llm.LlmClient;

import java.util.List;

public class TokenBudget {
    private final int contextWindow;
    private final int reservedForSystem;
    private final int reservedForTools;
    private final int reservedForResponse;

    private int totalInputTokens;
    private int totalOutputTokens;
    private int totalCachedInputTokens;
    private int llmCallCount;

    public TokenBudget(int contextWindow) {
        this(contextWindow, 500, 800, 2000);
    }

    public TokenBudget(int contextWindow, int reservedForSystem, int reservedForTools, int reservedForResponse) {
        this.contextWindow = contextWindow;
        this.reservedForSystem = reservedForSystem;
        this.reservedForTools = reservedForTools;
        this.reservedForResponse = reservedForResponse;
    }

    public int getAvailableForConversation() {
        return contextWindow - reservedForSystem - reservedForTools - reservedForResponse;
    }

    public boolean isWithinBudget(List<LlmClient.Message> messages) {
        return estimateMessagesTokens(messages) <= getAvailableForConversation();
    }

    public void recordUsage(int inputTokens, int outputTokens) {
        recordUsage(inputTokens, outputTokens, 0);
    }

    public void recordUsage(int inputTokens, int outputTokens, int cachedInputTokens) {
        totalInputTokens += inputTokens;
        totalOutputTokens += outputTokens;
        totalCachedInputTokens += Math.max(0, cachedInputTokens);
        llmCallCount++;
    }

    public String getUsageReport() {
        double avgInput = llmCallCount > 0 ? (double) totalInputTokens / llmCallCount : 0;
        return String.format(
                "Token stats: calls %d | input %d | output %d | cached %d | avg input %.0f | window %d (available %d)",
                llmCallCount, totalInputTokens, totalOutputTokens, totalCachedInputTokens, avgInput,
                contextWindow, getAvailableForConversation()
        );
    }

    public int getContextWindow() {
        return contextWindow;
    }

    public int getTotalInputTokens() {
        return totalInputTokens;
    }

    public int getTotalOutputTokens() {
        return totalOutputTokens;
    }

    public int getTotalCachedInputTokens() {
        return totalCachedInputTokens;
    }

    public int getLlmCallCount() {
        return llmCallCount;
    }

    public static int estimateMessagesTokens(List<LlmClient.Message> messages) {
        if (messages == null) {
            return 0;
        }
        int total = 0;
        for (LlmClient.Message msg : messages) {
            if (msg.contentParts() != null) {
                for (LlmClient.ContentPart part : msg.contentParts()) {
                    if (part == null) {
                        continue;
                    }
                    if (part.isText()) {
                        total += MemoryEntry.estimateTokens(part.text());
                    } else if (part.isImage()) {
                        total += estimateImageTokens(part);
                    }
                }
            } else {
                total += MemoryEntry.estimateTokens(msg.content());
            }
            if (msg.toolCalls() != null) {
                for (LlmClient.ToolCall toolCall : msg.toolCalls()) {
                    total += MemoryEntry.estimateTokens(toolCall.function().arguments());
                }
            }
        }
        return total + messages.size() * 4;
    }

    private static int estimateImageTokens(LlmClient.ContentPart part) {
        if (part.imageBase64() != null && !part.imageBase64().isBlank()) {
            int bytes = (int) (part.imageBase64().length() * 3L / 4L);
            return Math.max(256, Math.min(4096, bytes / 768));
        }
        return 1024;
    }
}
