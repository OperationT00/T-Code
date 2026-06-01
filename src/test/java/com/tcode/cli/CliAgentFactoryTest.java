package com.tcode.cli;

import com.tcode.agent.Agent;
import com.tcode.llm.GLMClient;
import com.tcode.mcp.McpServerManager;
import com.tcode.skill.SkillContextBuffer;
import com.tcode.skill.SkillRegistry;
import com.tcode.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CliAgentFactoryTest {

    @Test
    void createsInteractiveAgentWithSharedRegistryMcpContextAndSkills(@TempDir Path projectDir) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        McpServerManager mcpServerManager = new McpServerManager(registry, projectDir);
        SkillRegistry skillRegistry = new SkillRegistry(null, null, null, null);
        SkillContextBuffer skillContextBuffer = new SkillContextBuffer();

        Agent agent = CliAgentFactory.create(
                new GLMClient("test-key"),
                registry,
                mcpServerManager,
                skillRegistry,
                skillContextBuffer
        );

        assertSame(registry, agent.getToolRegistry());
        assertSame(skillRegistry, registry.getSkillRegistry());
        assertSame(skillContextBuffer, registry.getSkillContextBuffer());
        assertSame(skillRegistry, readField(agent, "skillRegistry"));
        assertSame(skillContextBuffer, readField(agent, "skillContextBuffer"));
        @SuppressWarnings("unchecked")
        Supplier<String> externalContextSupplier =
                (Supplier<String>) readField(agent, "externalContextSupplier");
        assertEquals(mcpServerManager.resourceIndexForPrompt(), externalContextSupplier.get());
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
