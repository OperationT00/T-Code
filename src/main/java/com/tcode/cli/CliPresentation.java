package com.tcode.cli;

import com.tcode.util.AnsiStyle;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

final class CliPresentation {
    private static final String VERSION = "1.0.2";

    record StartupScreenInfo(
            String model,
            String provider,
            long mcpReady,
            int mcpTotal,
            int mcpTools,
            int skillsEnabled,
            int skillsTotal,
            String note
    ) {
    }


    record SlashCommandHint(String insertText, String display, String description) {
    }

    private CliPresentation() {
    }

    static List<String> startupHints() {
        return List.of(
                "输入你的问题或任务",
                "输入 '/' 后按 Tab 补全命令",
                "输入 '@server:protocol://path' 可显式引用 MCP resource",
                "任务运行中按 ESC 取消当前任务",
                "默认模式是 ReAct"
        );
    }

    static List<SlashCommandHint> slashCommandHints() {
        SlashCommandHint compactHint = new SlashCommandHint(
                "/compact ",
                "/compact [focus]",
                "Manually compact conversation context");
        SlashCommandHint contextEventsHint = new SlashCommandHint(
                "/context events",
                "/context events",
                "List recent raw context events");
        SlashCommandHint contextRecallHint = new SlashCommandHint(
                "/context recall ",
                "/context recall <keyword>",
                "Search raw context events");
        SlashCommandHint contextShowHint = new SlashCommandHint(
                "/context show ",
                "/context show <event_id>",
                "Show a full raw context event");
        SlashCommandHint contextInjectHint = new SlashCommandHint(
                "/context inject ",
                "/context inject <event_id>",
                "Inject one raw context event explicitly");
        return List.of(
                compactHint,
                contextEventsHint,
                contextRecallHint,
                contextShowHint,
                contextInjectHint,
                new SlashCommandHint("/model", "/model", "查看当前模型"),
                new SlashCommandHint("/model glm-5.1", "/model glm-5.1", "切换到 GLM-5.1"),
                new SlashCommandHint("/model glm-5v-turbo", "/model glm-5v-turbo", "切换到 GLM-5V-Turbo 多模态"),
                new SlashCommandHint("/model deepseek", "/model deepseek", "切换到 DeepSeek（读取配置模型）"),
                new SlashCommandHint("/model step", "/model step", "切换到 StepFun（读取配置模型）"),
                new SlashCommandHint("/model kimi", "/model kimi", "切换到 Kimi（读取配置模型）"),
                new SlashCommandHint("/plan", "/plan", "下一条任务使用 Plan-and-Execute 模式"),
                new SlashCommandHint("/plan ", "/plan <任务内容>", "直接用计划模式执行这条任务"),
                new SlashCommandHint("/team", "/team", "下一条任务使用 Multi-Agent 协作模式"),
                new SlashCommandHint("/team ", "/team <任务内容>", "直接用多 Agent 协作执行这条任务"),
                new SlashCommandHint("/hitl", "/hitl", "查看 HITL 状态"),
                new SlashCommandHint("/hitl on", "/hitl on", "启用危险操作人工审批"),
                new SlashCommandHint("/hitl off", "/hitl off", "关闭 HITL 审批"),
                new SlashCommandHint("/browser", "/browser", "查看浏览器会话状态"),
                new SlashCommandHint("/browser connect", "/browser connect", "复用已允许远程调试的登录态 Chrome"),
                new SlashCommandHint("/browser connect ", "/browser connect <port>", "旧式 CDP 端口连接"),
                new SlashCommandHint("/browser status", "/browser status", "查看浏览器会话状态"),
                new SlashCommandHint("/browser tabs", "/browser tabs", "查看 shared 模式真实 Chrome tab"),
                new SlashCommandHint("/browser disconnect", "/browser disconnect", "切回 isolated 浏览器模式"),
                new SlashCommandHint("/task", "/task", "查看后台任务列表"),
                new SlashCommandHint("/task add ", "/task add <任务内容>", "提交后台任务"),
                new SlashCommandHint("/task cancel ", "/task cancel <task_id>", "取消后台任务"),
                new SlashCommandHint("/task log ", "/task log <task_id>", "查看后台任务结果"),
                new SlashCommandHint("/mcp", "/mcp", "查看 MCP server 状态"),
                new SlashCommandHint("/mcp restart ", "/mcp restart <name>", "重启 MCP server"),
                new SlashCommandHint("/mcp logs ", "/mcp logs <name>", "查看 MCP server 日志"),
                new SlashCommandHint("/mcp disable ", "/mcp disable <name>", "禁用 MCP server"),
                new SlashCommandHint("/mcp enable ", "/mcp enable <name>", "启用 MCP server"),
                new SlashCommandHint("/mcp resources ", "/mcp resources <name>", "查看 MCP resources"),
                new SlashCommandHint("/mcp prompts ", "/mcp prompts <name>", "查看 MCP prompts"),
                new SlashCommandHint("/policy", "/policy", "查看安全策略状态"),
                new SlashCommandHint("/config", "/config", "打开配置 palette（只读视图 + 切换提示）"),
                new SlashCommandHint("/audit", "/audit", "查看今日最近 10 条危险工具审计"),
                new SlashCommandHint("/audit ", "/audit [N]", "查看今日最近 N 条危险工具审计"),
                new SlashCommandHint("/snapshot", "/snapshot", "查看最近 Side-Git 快照"),
                new SlashCommandHint("/snapshot status", "/snapshot status", "查看 Side-Git 快照状态"),
                new SlashCommandHint("/snapshot clean", "/snapshot clean", "清理当前项目 Side-Git 快照"),
                new SlashCommandHint("/restore ", "/restore <N>", "恢复到最近第 N 个 pre-turn 快照"),
                new SlashCommandHint("/clear", "/clear", "清空当前对话历史"),
                new SlashCommandHint("/history clear", "/history clear", "清空本机输入历史"),
                new SlashCommandHint("/context", "/context", "查看上下文和记忆状态"),
                new SlashCommandHint("/memory", "/memory", "查看记忆状态"),
                new SlashCommandHint("/memory list", "/memory list", "查看长期记忆列表"),
                new SlashCommandHint("/memory open ", "/memory open [project|global]", "打开/定位 Markdown 记忆文件"),
                new SlashCommandHint("/memory search ", "/memory search <关键词>", "搜索当前项目可见长期记忆"),
                new SlashCommandHint("/memory delete ", "/memory delete <id>", "删除单条长期记忆"),
                new SlashCommandHint("/memory clear", "/memory clear", "清空长期记忆"),
                new SlashCommandHint("/save ", "/save [--global] <事实内容>", "手动保存项目级或全局长期记忆"),
                new SlashCommandHint("/skill", "/skill", "查看 skill 列表"),
                new SlashCommandHint("/skill list", "/skill list", "查看 skill 列表"),
                new SlashCommandHint("/skill show ", "/skill show <name>", "查看 SKILL.md 全文"),
                new SlashCommandHint("/skill on ", "/skill on <name>", "启用 skill"),
                new SlashCommandHint("/skill off ", "/skill off <name>", "禁用 skill"),
                new SlashCommandHint("/skill reload", "/skill reload", "重新扫描 skill 目录"),
                new SlashCommandHint("/exit", "/exit", "退出 t-code"),
                new SlashCommandHint("/quit", "/quit", "退出 t-code")
        );
    }

    static void printSlashCommandHelp(PrintStream out) {
        out.println("可用命令：");
        for (SlashCommandHint hint : slashCommandHints()) {
            out.println("   " + hint.display() + " - " + hint.description());
        }
        out.println();
    }

    static String formatSlashCommandChoices(int terminalWidth) {
        List<String> commands = slashCommandHints().stream()
                .map(SlashCommandHint::display)
                .distinct()
                .toList();
        int maxLen = commands.stream().mapToInt(String::length).max().orElse(12);
        int colWidth = Math.min(Math.max(maxLen + 4, 18), Math.max(18, terminalWidth));
        int columns = Math.max(1, Math.min(4, terminalWidth / colWidth));
        int rows = (int) Math.ceil(commands.size() / (double) columns);

        StringBuilder sb = new StringBuilder();
        sb.append("可用命令（Tab 补全，Enter 执行）：\n");
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int index = col * rows + row;
                if (index >= commands.size()) {
                    continue;
                }
                String command = commands.get(index);
                sb.append(command);
                if (col < columns - 1) {
                    sb.append(" ".repeat(Math.max(2, colWidth - command.length())));
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    static List<String> startupScreenLines(StartupScreenInfo info) {
        List<String> lines = new ArrayList<>(startupBannerLines(info));
        lines.add("");
        return lines;
    }

    static List<String> startupBannerLines() {
        return startupBannerLines(new StartupScreenInfo(
                "auto",
                "model",
                0,
                0,
                0,
                0,
                0,
                ""));
    }

    static List<String> startupBannerLines(StartupScreenInfo info) {
        String model = info.model() == null || info.model().isBlank() ? "auto" : info.model();
        String provider = info.provider() == null || info.provider().isBlank() ? "model" : info.provider();
        String mcp = info.mcpTotal() <= 0
                ? "MCP not configured"
                : "MCP " + info.mcpReady() + "/" + info.mcpTotal() + " · " + info.mcpTools() + " tools";
        String skills = info.skillsTotal() <= 0
                ? "0 skills"
                : info.skillsEnabled() + "/" + info.skillsTotal() + " skills";
        String ready = "Model " + model + " (" + provider + ")";
        String capabilities = "ReAct ? Plan ? MCP ? Browser ? Tools ? Memory";
        String state = mcp + " · " + skills + " · ReAct";
        List<String> lines = new ArrayList<>(List.of(
                "   " + AnsiStyle.section("██████████") + "    " + AnsiStyle.emphasis("t-code") + "  " + AnsiStyle.subtle("v" + VERSION),
                "   " + AnsiStyle.section("    ██    ") + "    " + AnsiStyle.subtle(ready),
                "   " + AnsiStyle.section("    ██    ") + "    " + AnsiStyle.subtle(state),
                "   " + AnsiStyle.section("    ██    ") + "    " + AnsiStyle.subtle(capabilities),
                "   " + AnsiStyle.section("    ██    "),
                "",
                "Tips for getting started:",
                "1. Type " + AnsiStyle.emphasis("/") + " for commands and Tab completion",
                "2. Ask coding questions, edit code or run commands",
                "3. Attach context with " + AnsiStyle.emphasis("@path") + " or " + AnsiStyle.emphasis("@image:")
        ));
        if (info.note() != null && !info.note().isBlank()) {
            lines.add("");
            lines.add(AnsiStyle.subtle(info.note().replace('\n', ' ')));
        }
        return lines;
    }
}
