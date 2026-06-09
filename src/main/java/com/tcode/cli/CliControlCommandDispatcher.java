package com.tcode.cli;

import com.tcode.browser.BrowserConnectivityCheck;
import com.tcode.browser.BrowserAuditMetadata;
import com.tcode.browser.BrowserSession;
import com.tcode.hitl.ApprovalPolicy;
import com.tcode.hitl.HitlToolRegistry;
import com.tcode.hitl.SwitchableHitlHandler;
import com.tcode.mcp.McpServerManager;
import com.tcode.memory.MemoryEntry;
import com.tcode.memory.MemoryManager;
import com.tcode.policy.AuditLog;
import com.tcode.runtime.task.DurableTaskManager;
import com.tcode.runtime.task.TaskCommandFormatter;
import com.tcode.snapshot.RestoreResult;
import com.tcode.snapshot.SnapshotService;
import com.tcode.snapshot.TurnSnapshot;
import com.tcode.skill.SkillRegistry;
import com.tcode.skill.SkillStateStore;
import com.tcode.tool.ToolRegistry;
import org.jline.reader.LineReader;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

final class CliControlCommandDispatcher {

    record Context(
            PrintStream ui,
            LineReader lineReader,
            SwitchableHitlHandler hitlHandler,
            McpServerManager mcpServerManager,
            BrowserSession browserSession,
            BrowserConnectivityCheck browserConnectivityCheck,
            HitlToolRegistry hitlToolRegistry,
            DurableTaskManager taskManager,
            SkillRegistry skillRegistry,
            SkillStateStore skillStateStore,
            MemoryManager memoryManager,
            ToolRegistry toolRegistry,
            Runnable refreshStatus
    ) {
    }

    private CliControlCommandDispatcher() {
    }

    static boolean dispatch(CliCommandParser.ParsedCommand command, Context context) {
        return switch (command.type()) {
            case HISTORY_CLEAR -> {
                CliInputHistory.clearLineReaderHistory(context.lineReader());
                context.ui().println("🧹 输入历史已清空\n");
                yield true;
            }
            case SWITCH_HITL -> {
                handleHitl(command.payload(), context);
                yield true;
            }
            case MEMORY_STATUS -> {
                printMemoryStatus(context.ui(), context.memoryManager());
                yield true;
            }
            case MEMORY_LIST -> {
                context.ui().println(formatMemoryEntries(
                        "📋 长期记忆列表", context.memoryManager().listLongTerm(),
                        context.memoryManager()));
                context.ui().println();
                yield true;
            }
            case MEMORY_SEARCH -> {
                handleMemorySearch(command.payload(), context);
                yield true;
            }
            case MEMORY_OPEN -> {
                handleMemoryOpen(command.payload(), context);
                yield true;
            }
            case MEMORY_DELETE -> {
                handleMemoryDelete(command.payload(), context);
                yield true;
            }
            case MEMORY_CLEAR -> {
                context.memoryManager().clearLongTerm();
                context.ui().println("🧹 长期记忆已清空\n");
                context.ui().println();
                yield true;
            }
            case MEMORY_SAVE -> {
                handleMemorySave(command.payload(), context);
                yield true;
            }
            case POLICY_STATUS -> {
                printPolicyStatus(context.ui(), context.toolRegistry());
                yield true;
            }
            case AUDIT_TAIL -> {
                printAuditTail(context.ui(), context.toolRegistry().getAuditLog(), command.payload());
                yield true;
            }
            case SNAPSHOT -> {
                printSnapshotCommand(
                        context.ui(), context.toolRegistry().getSnapshotService(), command.payload());
                yield true;
            }
            case RESTORE_SNAPSHOT -> {
                printRestoreCommand(
                        context.ui(), context.toolRegistry().getSnapshotService(), command.payload());
                yield true;
            }
            case MCP_LIST -> {
                context.ui().println(context.mcpServerManager().formatStatus());
                context.ui().println();
                yield true;
            }
            case MCP_RESTART -> {
                printResult(context.ui(), context.mcpServerManager().restart(command.payload()));
                refreshStatus(context);
                yield true;
            }
            case MCP_LOGS -> {
                printResult(context.ui(), context.mcpServerManager().logs(command.payload()));
                yield true;
            }
            case MCP_DISABLE -> {
                printResult(context.ui(), context.mcpServerManager().disable(command.payload()));
                refreshStatus(context);
                yield true;
            }
            case MCP_ENABLE -> {
                printResult(context.ui(), context.mcpServerManager().enable(command.payload()));
                refreshStatus(context);
                yield true;
            }
            case MCP_RESOURCES -> {
                printResult(context.ui(), context.mcpServerManager().resources(command.payload()));
                yield true;
            }
            case MCP_PROMPTS -> {
                printResult(context.ui(), context.mcpServerManager().prompts(command.payload()));
                yield true;
            }
            case BROWSER -> {
                printResult(context.ui(), CliBrowserCommandHandler.handle(
                        command.payload(),
                        context.browserSession(),
                        context.browserConnectivityCheck(),
                        context.mcpServerManager(),
                        context.hitlToolRegistry(),
                        context.hitlHandler()));
                yield true;
            }
            case TASK -> {
                printResult(context.ui(), TaskCommandFormatter.handle(context.taskManager(), command.payload()));
                yield true;
            }
            case SKILL_LIST -> {
                context.ui().println(SkillCommandHandler.list(context.skillRegistry()));
                yield true;
            }
            case SKILL_SHOW -> {
                context.ui().println(SkillCommandHandler.show(context.skillRegistry(), command.payload()));
                yield true;
            }
            case SKILL_ON -> {
                context.ui().println(SkillCommandHandler.enable(
                        context.skillRegistry(), context.skillStateStore(), command.payload()));
                refreshStatus(context);
                yield true;
            }
            case SKILL_OFF -> {
                context.ui().println(SkillCommandHandler.disable(
                        context.skillRegistry(), context.skillStateStore(), command.payload()));
                refreshStatus(context);
                yield true;
            }
            case SKILL_RELOAD -> {
                context.skillRegistry().reload();
                context.ui().println("🔄 已重新扫描 skill 目录");
                context.ui().println(SkillCommandHandler.startupSummary(context.skillRegistry()));
                context.ui().println("✅ 下一轮 LLM 调用生效");
                refreshStatus(context);
                yield true;
            }
            default -> false;
        };
    }

    private static void handleHitl(String payload, Context context) {
        if ("on".equals(payload)) {
            context.hitlHandler().setEnabled(true);
            context.ui().println("🔐 HITL 审批已启用：write_file / execute_command / create_project 执行前将请求人工确认\n");
        } else if ("off".equals(payload)) {
            context.hitlHandler().setEnabled(false);
            context.hitlHandler().clearApprovedAll();
            context.ui().println("🔓 HITL 审批已关闭：危险操作将直接执行\n");
        } else {
            String status = context.hitlHandler().isEnabled() ? "启用" : "关闭";
            context.ui().println("🔐 HITL 当前状态：" + status);
            context.ui().println("   /hitl on  - 启用人工审批");
            context.ui().println("   /hitl off - 关闭人工审批\n");
        }
        refreshStatus(context);
    }

    private static void printMemoryStatus(PrintStream out, MemoryManager memoryManager) {
        out.println("📋 记忆系统状态：");
        out.println(memoryManager.getSystemStatus());
        out.println("   当前项目作用域: " + memoryManager.getCurrentProject());
        out.println("   /memory list - 查看长期记忆");
        out.println("   /memory open [project|global] - 打开/定位 Markdown 记忆文件");
        out.println("   /memory search <关键词> - 搜索当前项目可见长期记忆");
        out.println("   /memory delete <id> - 删除单条长期记忆");
        out.println("   /memory clear - 清空长期记忆");
        out.println("   /save <事实> - 保存项目级长期记忆；/save --global <事实> 保存全局记忆");
        out.println();
    }

    private static void handleMemorySearch(String payload, Context context) {
        if (payload == null || payload.isBlank()) {
            context.ui().println("❌ 请提供搜索关键词，例如 /memory search Chrome 登录态\n");
            return;
        }
        context.ui().println(formatMemoryEntries(
                "🔎 长期记忆搜索: " + payload, context.memoryManager().searchLongTerm(payload, 20),
                context.memoryManager()));
        context.ui().println();
    }

    private static void handleMemoryOpen(String payload, Context context) {
        String scope = payload == null || payload.isBlank() ? "project" : payload.trim();
        if (!scope.equalsIgnoreCase("project") && !scope.equalsIgnoreCase("global") && !scope.equalsIgnoreCase("user")) {
            context.ui().println("❌ /memory open 只支持 project 或 global\n");
            return;
        }
        String normalizedScope = scope.equalsIgnoreCase("user") ? "global" : scope.toLowerCase();
        Path file = context.memoryManager().ensureMemoryFile(normalizedScope);
        context.ui().println("📝 Memory file (" + normalizedScope + "): " + file);
        context.ui().println("   可直接编辑这个 Markdown 文件；保存后下一轮会读取。\n");
    }

    private static void handleMemoryDelete(String payload, Context context) {
        if (payload == null || payload.isBlank()) {
            context.ui().println("❌ 请提供要删除的记忆 id，例如 /memory delete fact-abcd1234\n");
        } else if (context.memoryManager().deleteLongTerm(payload)) {
            context.ui().println("🗑️ 已删除长期记忆: " + payload + "\n");
        } else {
            context.ui().println("📭 未找到长期记忆: " + payload + "\n");
        }
    }

    private static void handleMemorySave(String payload, Context context) {
        MemorySaveRequest saveRequest = parseMemorySave(payload);
        if (saveRequest.fact().isEmpty()) {
            context.ui().println("❌ 请提供要保存的内容，例如 /save 这个项目使用Java 17，或 /save --global 默认用中文回答\n");
            return;
        }
        context.memoryManager().storeFact(saveRequest.fact(), saveRequest.scope());
        context.ui().println("💾 已保存到长期记忆(" + saveRequest.scope() + "): " + saveRequest.fact() + "\n");
    }

    private static void printPolicyStatus(PrintStream out, ToolRegistry toolRegistry) {
        out.println("🛡️ 安全策略状态：");
        out.println("   项目根: " + toolRegistry.getProjectPath());
        out.println("   危险工具: " + String.join(", ", ApprovalPolicy.getDangerousTools()) + "，以及所有 mcp__ 前缀工具");
        out.println("   路径围栏: 强制限定在项目根之内（read_file / write_file / list_dir / create_project）");
        out.println("   命令黑名单: sudo / rm -rf 全盘 / mkfs / dd of=/dev / fork bomb / curl|sh / find / / chmod 777 / / shutdown");
        out.println("   写入文件上限: 5MB");
        out.println("   命令执行上限: 60 秒，输出 8KB（截断）");
        out.println("   审计目录: " + toolRegistry.getAuditLog().getAuditDir());
        out.println();
    }

    private static void printAuditTail(PrintStream out, AuditLog auditLog, String payload) {
        int requested = parseBoundedCount(payload, 10);
        List<AuditLog.AuditEntry> entries = auditLog.readRecent(requested);
        if (entries.isEmpty()) {
            out.println("📭 今日尚无审计记录\n");
            return;
        }
        out.println("📋 最近 " + entries.size() + " 条危险工具审计：");
        for (AuditLog.AuditEntry entry : entries) {
            out.printf("   [%s] %s %s (%dms, approver=%s)%n",
                    entry.outcome().toUpperCase(),
                    entry.timestamp(),
                    entry.tool(),
                    entry.durationMs(),
                    entry.approver());
            if (entry.reason() != null && !entry.reason().isBlank()) {
                out.println("        原因: " + entry.reason());
            }
            BrowserAuditMetadata metadata = entry.metadata();
            if (metadata != null) {
                out.println("        浏览器: mode=" + metadata.browserMode()
                        + ", sensitive=" + metadata.sensitive()
                        + (metadata.targetUrl() == null ? "" : ", url=" + metadata.targetUrl()));
            }
        }
        out.println();
    }

    private static void printSnapshotCommand(PrintStream out, SnapshotService snapshotService, String payload) {
        String normalized = payload == null || payload.isBlank() ? "list" : payload.trim().toLowerCase();
        if ("status".equals(normalized)) {
            printResult(out, snapshotService.status());
            return;
        }
        if ("clean".equals(normalized)) {
            printResult(out, snapshotService.clean());
            return;
        }
        if (!"list".equals(normalized)) {
            out.println("""
                    ❌ 未知 /snapshot 子命令: %s
                    可用命令：
                      /snapshot
                      /snapshot status
                      /snapshot clean
                      /restore <N>
                    """.formatted(payload).trim());
            out.println();
            return;
        }
        try {
            List<TurnSnapshot> snapshots = snapshotService.listSnapshots(20);
            if (snapshots.isEmpty()) {
                out.println("📭 暂无 Side-Git 快照\n");
                return;
            }
            out.println("📸 最近 " + snapshots.size() + " 条 Side-Git 快照：");
            int preTurnIndex = 0;
            for (TurnSnapshot snapshot : snapshots) {
                String restoreHint = "";
                if ("pre-turn".equals(snapshot.phase().label())) {
                    preTurnIndex++;
                    restoreHint = "  /restore " + preTurnIndex;
                }
                out.printf("   %s %-11s %-18s %s%s%n",
                        snapshot.shortCommitId(),
                        snapshot.phase().label(),
                        snapshot.turnId(),
                        snapshot.createdAt(),
                        restoreHint);
            }
            out.println();
        } catch (Exception e) {
            out.println("❌ 读取快照失败: " + e.getMessage() + "\n");
        }
    }

    private static void printRestoreCommand(PrintStream out, SnapshotService snapshotService, String payload) {
        int offset = parseBoundedCount(payload, 1);
        try {
            RestoreResult result = snapshotService.restorePreTurn(offset);
            printResult(out, result.formatForCli());
        } catch (Exception e) {
            out.println("❌ 恢复快照失败: " + e.getMessage() + "\n");
        }
    }

    private static int parseBoundedCount(String payload, int defaultN) {
        if (payload == null || payload.isBlank()) return defaultN;
        try {
            int n = Integer.parseInt(payload.trim());
            return Math.max(1, Math.min(n, 100));
        } catch (NumberFormatException e) {
            return defaultN;
        }
    }

    private static MemorySaveRequest parseMemorySave(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.regionMatches(true, 0, "--global ", 0, 9)) {
            return new MemorySaveRequest(value.substring(9).trim(), "global");
        }
        if (value.equalsIgnoreCase("--global")) {
            return new MemorySaveRequest("", "global");
        }
        if (value.regionMatches(true, 0, "--project ", 0, 10)) {
            return new MemorySaveRequest(value.substring(10).trim(), "project");
        }
        if (value.equalsIgnoreCase("--project")) {
            return new MemorySaveRequest("", "project");
        }
        return new MemorySaveRequest(value, "project");
    }

    private static String formatMemoryEntries(String title, List<MemoryEntry> entries) {
        return formatMemoryEntries(title, entries, null);
    }

    private static String formatMemoryEntries(String title, List<MemoryEntry> entries, MemoryManager memoryManager) {
        StringBuilder sb = new StringBuilder(title).append("：\n");
        if (memoryManager != null) {
            sb.append("  project file: ")
                    .append(memoryManager.getMarkdownStore().projectFile())
                    .append("\n")
                    .append("  user file: ")
                    .append(memoryManager.getMarkdownStore().globalFile())
                    .append("\n");
        }
        if (entries == null || entries.isEmpty()) {
            return sb.append("📭 没有匹配的长期记忆。").toString();
        }
        for (MemoryEntry entry : entries) {
            String scope = entry.getMetadata().getOrDefault("scope", "project");
            String project = entry.getMetadata().get("project");
            sb.append("- ")
                    .append(entry.getId())
                    .append(" [").append(scope).append("]");
            if ("project".equals(scope) && project != null && !project.isBlank()) {
                sb.append(" ").append(shortenPath(project));
            }
            sb.append(" · ").append(entry.getTimestamp()).append("\n")
                    .append("  ").append(entry.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    private static String shortenPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        try {
            Path p = Path.of(path);
            int count = p.getNameCount();
            if (count <= 3) {
                return path;
            }
            return "..." + File.separator + p.subpath(count - 3, count);
        } catch (Exception e) {
            return path;
        }
    }

    private static void refreshStatus(Context context) {
        if (context.refreshStatus() != null) {
            context.refreshStatus().run();
        }
    }

    private static void printResult(PrintStream out, String result) {
        out.println(result);
        out.println();
    }

    private record MemorySaveRequest(String fact, String scope) {
    }
}
