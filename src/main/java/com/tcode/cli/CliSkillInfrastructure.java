package com.tcode.cli;

import com.tcode.skill.SkillBuiltinExtractor;
import com.tcode.skill.SkillContextBuffer;
import com.tcode.skill.SkillRegistry;
import com.tcode.skill.SkillStateStore;

import java.nio.file.Path;

record CliSkillInfrastructure(
        SkillRegistry skillRegistry,
        SkillContextBuffer skillContextBuffer,
        String startupNote
) {

    static CliSkillInfrastructure create(Path home, Path projectDir) {
        Path skillsCacheDir = home.resolve(".tcode/skills-cache");
        Path userSkillsDir = home.resolve(".tcode/skills");
        Path projectSkillsDir = projectDir.resolve(".tcode/skills").toAbsolutePath();
        String startupNote = "";
        try {
            new SkillBuiltinExtractor(skillsCacheDir).extractAll();
        } catch (Exception e) {
            startupNote = "内置 skill 解压失败: " + e.getMessage();
        }
        SkillStateStore skillStateStore = new SkillStateStore(home.resolve(".tcode/skills.json"));
        SkillRegistry skillRegistry = new SkillRegistry(
                skillsCacheDir, userSkillsDir, projectSkillsDir, skillStateStore);
        skillRegistry.reload();
        return new CliSkillInfrastructure(skillRegistry, new SkillContextBuffer(), startupNote);
    }
}
