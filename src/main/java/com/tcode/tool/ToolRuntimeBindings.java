package com.tcode.tool;

import com.tcode.browser.BrowserConnector;
import com.tcode.skill.SkillContextBuffer;
import com.tcode.skill.SkillRegistry;

import java.util.function.BiConsumer;

public final class ToolRuntimeBindings {
    private BrowserConnector browserConnector;
    private BiConsumer<String, String> memorySaver;
    private SkillRegistry skillRegistry;
    private SkillContextBuffer skillContextBuffer;

    public BrowserConnector browserConnector() {
        return browserConnector;
    }

    public void setBrowserConnector(BrowserConnector browserConnector) {
        this.browserConnector = browserConnector;
    }

    public BiConsumer<String, String> memorySaver() {
        return memorySaver;
    }

    public void setMemorySaver(BiConsumer<String, String> memorySaver) {
        this.memorySaver = memorySaver;
    }

    public SkillRegistry skillRegistry() {
        return skillRegistry;
    }

    public void setSkillRegistry(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public SkillContextBuffer skillContextBuffer() {
        return skillContextBuffer;
    }

    public void setSkillContextBuffer(SkillContextBuffer skillContextBuffer) {
        this.skillContextBuffer = skillContextBuffer;
    }
}
