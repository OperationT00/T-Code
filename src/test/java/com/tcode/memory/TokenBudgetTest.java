package com.tcode.memory;

import com.tcode.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenBudgetTest {

    @Test
    void shouldCalculateAvailableTokens() {
        TokenBudget budget = new TokenBudget(128000);
        int available = budget.getAvailableForConversation();
        // 128000 - 500(system) - 800(tools) - 2000(response)
        assertEquals(124700, available);
    }

    @Test
    void shouldTrackUsageStats() {
        TokenBudget budget = new TokenBudget(128000);
        budget.recordUsage(1000, 500);
        budget.recordUsage(1200, 600);

        assertEquals(2200, budget.getTotalInputTokens());
        assertEquals(1100, budget.getTotalOutputTokens());
        assertEquals(2, budget.getLlmCallCount());
    }

    @Test
    void shouldEstimateMessageTokens() {
        List<LlmClient.Message> messages = List.of(
                LlmClient.Message.system("系统提示"),
                LlmClient.Message.user("用户消息")
        );

        int tokens = TokenBudget.estimateMessagesTokens(messages);
        assertTrue(tokens > 0);
        // 至少包含两条消息的开销
        assertTrue(tokens >= 8); // 2 messages * 4 overhead
    }

    @Test
    void shouldGenerateUsageReport() {
        TokenBudget budget = new TokenBudget(128000);
        budget.recordUsage(1000, 500);

        String report = budget.getUsageReport();
                assertTrue(report.contains("calls 1"));
        assertTrue(report.contains("1000"));
    }
}
