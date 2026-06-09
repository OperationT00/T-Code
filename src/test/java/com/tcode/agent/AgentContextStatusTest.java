package com.tcode.agent;

import com.tcode.llm.GLMClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentContextStatusTest {

    @Test
    void contextStatusShowsContextHealthMetrics() {
        Agent agent = new Agent(new GLMClient("test-key"));

        String status = agent.getContextStatus();

        assertTrue(status.contains("Messages:"), status);
        assertTrue(status.contains("Context pressure:"), status);
        assertTrue(status.contains("Tool summary policy:"), status);
        assertTrue(status.contains("Tool summaries:"), status);
        assertTrue(status.contains("History compactions:"), status);
    }
}
