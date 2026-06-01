package com.tcode.tool;

import com.tcode.skill.Skill;
import com.tcode.skill.SkillContextBuffer;
import com.tcode.skill.SkillRegistry;

import java.util.function.Supplier;

public final class SkillToolsProvider implements ToolProvider {
    private static final int MAX_SKILL_BODY_CHARS = 5 * 1024;

    private final Supplier<SkillRegistry> registrySupplier;
    private final Supplier<SkillContextBuffer> bufferSupplier;

    public SkillToolsProvider(Supplier<SkillRegistry> registrySupplier,
                              Supplier<SkillContextBuffer> bufferSupplier) {
        this.registrySupplier = registrySupplier == null ? () -> null : registrySupplier;
        this.bufferSupplier = bufferSupplier == null ? () -> null : bufferSupplier;
    }

    @Override
    public void register(ToolRegistrationContext context) {
        context.register(
                "load_skill",
                "Load full SKILL.md instructions for an enabled skill indexed in the system prompt.",
                context.parameters(
                        context.param("name", "string", "Exact kebab-case skill name, for example web-access.", true)
                ),
                args -> load(args.get("name"))
        );
    }

    private String load(String name) {
        if (name == null || name.isBlank()) {
            return "load_skill 失败: name 不能为空";
        }
        SkillRegistry registry = registrySupplier.get();
        if (registry == null) {
            return "load_skill 失败: Skill 系统未初始化";
        }
        Skill skill = registry.findSkill(name);
        if (skill == null) {
            Skill any = registry.findAnySkill(name);
            if (any == null) {
                return "Skill '" + name + "' 未找到，可用 /skill list 查看可用 skill";
            }
            return "Skill '" + name + "' 已被禁用，可用 /skill on " + name + " 启用";
        }
        String body = skill.body() == null ? "" : skill.body();
        String injected = body;
        if (injected.length() > MAX_SKILL_BODY_CHARS) {
            injected = injected.substring(0, MAX_SKILL_BODY_CHARS)
                    + "\n\n...(skill body truncated, full content via /skill show " + name + ")";
        }
        SkillContextBuffer buffer = bufferSupplier.get();
        if (buffer != null) {
            buffer.push(name, injected);
        }
        return "已加载 skill '" + name + "' 的完整指引（" + body.length()
                + " bytes），将在下一轮上下文中以 \"## 已加载 Skill：" + name + "\" 段出现。";
    }
}
