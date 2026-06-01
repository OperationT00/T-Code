package com.tcode.cli;

import com.tcode.agent.Agent;
import com.tcode.agent.AgentOrchestrator;
import com.tcode.agent.PlanExecuteAgent;
import com.tcode.llm.LlmClient;
import com.tcode.mcp.McpServerManager;
import com.tcode.skill.SkillContextBuffer;
import com.tcode.skill.SkillRegistry;
import com.tcode.tool.ToolRegistry;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

import java.io.PrintStream;

final class CliAgentFactory {

    private CliAgentFactory() {
    }

    static Agent create(LlmClient llmClient,
                        ToolRegistry toolRegistry,
                        McpServerManager mcpServerManager,
                        SkillRegistry skillRegistry,
                        SkillContextBuffer skillContextBuffer) {
        toolRegistry.setSkillRegistry(skillRegistry);
        toolRegistry.setSkillContextBuffer(skillContextBuffer);

        Agent agent = new Agent(llmClient, toolRegistry);
        agent.setExternalContextSupplier(mcpServerManager::resourceIndexForPrompt);
        agent.setSkillRegistry(skillRegistry);
        agent.setSkillContextBuffer(skillContextBuffer);
        return agent;
    }

    static PlanExecuteAgent createPlan(LlmClient llmClient,
                                       Agent reactAgent,
                                       PlanExecuteAgent.PlanReviewHandler reviewHandler,
                                       PrintStream out) {
        return new PlanExecuteAgent(
                llmClient,
                reactAgent.getToolRegistry(),
                reactAgent.getMemoryManager(),
                reviewHandler,
                out
        );
    }

    static PlanExecuteAgent createInteractivePlan(LlmClient llmClient,
                                                  Agent reactAgent,
                                                  Terminal terminal,
                                                  LineReader lineReader,
                                                  PrintStream out) {
        out.println("📋 使用 Plan-and-Execute 模式\n");
        return createPlan(llmClient, reactAgent, CliPlanReviewHandler.create(terminal, lineReader, out), out);
    }

    static AgentOrchestrator createTeam(LlmClient llmClient, Agent reactAgent, PrintStream out) {
        out.println("👥 使用 Multi-Agent 协作模式\n");
        return new AgentOrchestrator(llmClient, reactAgent.getToolRegistry(), reactAgent.getMemoryManager(), out);
    }
}
