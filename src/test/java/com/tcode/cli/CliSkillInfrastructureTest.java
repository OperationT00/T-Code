package com.tcode.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliSkillInfrastructureTest {

    @Test
    void createsBuiltinRegistryStateStoreAndContextBuffer(@TempDir Path tempDir) {
        Path home = tempDir.resolve("home");
        Path projectDir = tempDir.resolve("project");

        CliSkillInfrastructure infrastructure = CliSkillInfrastructure.create(home, projectDir);

        assertNotNull(infrastructure.skillRegistry().findSkill("web-access"));
        assertEquals(home.resolve(".tcode/skills.json"),
                infrastructure.skillRegistry().stateStore().file());
        assertNotNull(infrastructure.skillContextBuffer());
        assertTrue(infrastructure.startupNote().isBlank());
    }
}
