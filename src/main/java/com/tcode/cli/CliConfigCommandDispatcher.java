package com.tcode.cli;

import com.tcode.config.TCodeConfig;
import com.tcode.hitl.SwitchableHitlHandler;
import com.tcode.llm.LlmClient;
import com.tcode.render.Renderer;
import com.tcode.skill.SkillRegistry;

import java.util.List;
import java.util.function.Supplier;

final class CliConfigCommandDispatcher {

    record Context(
            Renderer renderer,
            TCodeConfig config,
            Supplier<LlmClient> currentClient,
            SwitchableHitlHandler hitlHandler,
            SkillRegistry skillRegistry
    ) {
    }

    private CliConfigCommandDispatcher() {
    }

    static boolean dispatch(CliCommandParser.ParsedCommand command, Context context) {
        if (command.type() != CliCommandParser.CommandType.CONFIG) {
            return false;
        }
        openPalette(context);
        return true;
    }

    private static void openPalette(Context context) {
        LlmClient llmClient = context.currentClient() == null ? null : context.currentClient().get();
        var items = List.of(
                "模型: " + (llmClient == null ? "(none)" : llmClient.getModelName() + " / " + llmClient.getProviderName()),
                "默认 Provider: " + (context.config() == null ? "(none)" : context.config().getDefaultProvider()),
                "HITL: " + (context.hitlHandler().isEnabled() ? "ON" : "OFF"),
                "Skill 启用数: " + (context.skillRegistry() == null ? 0 : context.skillRegistry().enabledSkills().size()),
                "渲染器: " + context.renderer().getClass().getSimpleName(),
                "配置文件: ~/.tcode/config.json (只读视图，编辑请用编辑器)"
        );
        int selected = context.renderer().openPalette("配置 / config", items);
        if (selected < 0) {
            context.renderer().stream().println("(已关闭)");
            return;
        }
        String hint = switch (selected) {
            case 0, 1 -> "💡 GLM: /model glm-5.1 / /model glm-5v-turbo；其它: /model deepseek|step|kimi 读取配置模型";
            case 2 -> "💡 切换 HITL: /hitl on / /hitl off";
            case 3 -> "💡 管理 Skill: /skill list / /skill on <name> / /skill off <name>";
            case 4 -> "💡 切换渲染器（重启后生效）: TCODE_RENDERER=inline|lanterna|plain";
            case 5 -> "💡 当前不在 TUI 内编辑 config.json，建议在编辑器里改完重启";
            default -> "(unknown)";
        };
        context.renderer().stream().println(hint);
    }
}
