package com.tcode.tool;

import com.tcode.skill.Skill;
import com.tcode.skill.SkillContextBuffer;
import com.tcode.skill.SkillRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillToolsProviderTest {

    @Test
    void registersLoadSkillWithInjectedRegistryAndBuffer(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("skills").resolve("demo-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: demo-skill
                description: Demo
                ---
                Follow the demo instructions.
                """);
        SkillRegistry registry = new SkillRegistry(null, tempDir.resolve("skills"), null, null);
        registry.reload();
        SkillContextBuffer buffer = new SkillContextBuffer();
        AtomicReference<SkillRegistry> registryRef = new AtomicReference<>(registry);
        AtomicReference<SkillContextBuffer> bufferRef = new AtomicReference<>(buffer);
        ToolRegistry tools = new ToolRegistry();
        tools.registerProvider(new SkillToolsProvider(registryRef::get, bufferRef::get));

        String result = tools.executeTool("load_skill", "{\"name\":\"demo-skill\"}");

        assertTrue(result.contains("demo-skill"));
        assertFalse(buffer.isEmpty());
        assertTrue(buffer.drain().contains("Follow the demo instructions."));
    }
}
