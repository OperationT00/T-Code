package com.tcode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.tcode.browser.BrowserCheckResult;
import com.tcode.browser.BrowserConnector;
import com.tcode.browser.BrowserGuard;
import com.tcode.context.ContextProfile;
import com.tcode.lsp.LspDiagnosticReport;
import com.tcode.lsp.LspManager;
import com.tcode.mcp.protocol.McpToolDescriptor;
import com.tcode.policy.AuditLog;
import com.tcode.policy.PathGuard;
import com.tcode.snapshot.SnapshotService;
import com.tcode.skill.SkillContextBuffer;
import com.tcode.skill.SkillRegistry;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 工具注册表 - 管理所有可用工具
 */
public class ToolRegistry {
    private static final int DEFAULT_COMMAND_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_TOOL_BATCH_TIMEOUT_SECONDS = 90;
    private static final int MAX_PARALLEL_TOOLS = 4;
    private final ToolDefinitionCatalog toolDefinitions = new ToolDefinitionCatalog();
    private final McpToolCatalog mcpToolCatalog = new McpToolCatalog();
    private String projectPath = System.getProperty("user.dir");
    private PathGuard pathGuard = new PathGuard(projectPath);
    private final AuditLog auditLog = new AuditLog();
    private ContextProfile contextProfile = ContextProfile.from(null);
    private BrowserGuard browserGuard;
    private final ToolRuntimeBindings runtimeBindings = new ToolRuntimeBindings();
    private java.util.function.BiConsumer<String, String[]> writeFileObserver = (p, ba) -> {};
    private LspManager lspManager = new LspManager(projectPath);
    private SnapshotService snapshotService = SnapshotService.forProject(Path.of(projectPath));
    private boolean customSnapshotService;
    private ToolLifecycleListener toolLifecycleListener = ToolLifecycleListener.NO_OP;
    private final FileService fileService = new FileService(
            () -> pathGuard,
            () -> writeFileObserver,
            this::runPostEditLspHook);
    private final ProjectScaffolder projectScaffolder = new ProjectScaffolder(() -> pathGuard);
    private final FileSearchService fileSearchService = new FileSearchService(() -> pathGuard);
    private final WebService webService = new WebService();
    private final ShellService shellService;
    private final ToolBatchExecutor batchExecutor;
    private final ToolExecutionPipeline executionPipeline;
    private final Deque<ToolTraceEvent> toolTraceEvents = new ConcurrentLinkedDeque<>();

    public ToolRegistry() {
        this(DEFAULT_COMMAND_TIMEOUT_SECONDS, DEFAULT_TOOL_BATCH_TIMEOUT_SECONDS);
    }

    ToolRegistry(long commandTimeoutSeconds) {
        this(commandTimeoutSeconds, Math.max(commandTimeoutSeconds + 5, DEFAULT_TOOL_BATCH_TIMEOUT_SECONDS));
    }

    ToolRegistry(long commandTimeoutSeconds, long toolBatchTimeoutSeconds) {
        this.shellService = new ShellService(() -> projectPath, commandTimeoutSeconds);
        this.batchExecutor = new ToolBatchExecutor(toolBatchTimeoutSeconds, MAX_PARALLEL_TOOLS);
        this.executionPipeline = new ToolExecutionPipeline(toolDefinitions, mcpToolCatalog, auditLog, () -> browserGuard);
        // 注册内置工具
        registerProvider(new FileToolsProvider(fileService::read, fileService::write, fileService::list));
        registerProvider(new FileSearchToolsProvider(fileSearchService::glob, fileSearchService::grep));
        registerProvider(new ShellToolsProvider(shellService::execute));
        registerProvider(new ProjectToolsProvider(projectScaffolder::create));
        registerProvider(new WebToolsProvider(webService::search, webService::fetch));
        registerProvider(new BrowserToolsProvider(runtimeBindings::browserConnector));
        registerProvider(new MemoryToolsProvider(runtimeBindings::memorySaver));
        registerProvider(new SkillToolsProvider(runtimeBindings::skillRegistry, runtimeBindings::skillContextBuffer));
        registerProvider(new SnapshotToolsProvider(() -> snapshotService));
    }

    /**
     * 设置代码检索的项目路径
     */
    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
        this.pathGuard = new PathGuard(projectPath);
        this.lspManager.setProjectPath(projectPath);
        if (!customSnapshotService) {
            this.snapshotService.close();
            this.snapshotService = SnapshotService.forProject(Path.of(projectPath));
        }
    }

    /**
     * 获取代码检索的项目路径
     */
    public String getProjectPath() {
        return projectPath;
    }

    public void setContextProfile(ContextProfile contextProfile) {
        if (contextProfile != null) {
            this.contextProfile = contextProfile;
        }
    }

    public ContextProfile getContextProfile() {
        return contextProfile;
    }

    public void setBrowserGuard(BrowserGuard browserGuard) {
        this.browserGuard = browserGuard;
    }

    protected BrowserGuard getBrowserGuard() {
        return browserGuard;
    }

    public void setBrowserConnector(BrowserConnector browserConnector) {
        runtimeBindings.setBrowserConnector(browserConnector);
    }

    public void setMemorySaver(Consumer<String> memorySaver) {
        runtimeBindings.setMemorySaver(memorySaver == null ? null : (fact, scope) -> memorySaver.accept(fact));
    }

    public void setScopedMemorySaver(BiConsumer<String, String> memorySaver) {
        runtimeBindings.setMemorySaver(memorySaver);
    }

    public void setSkillRegistry(SkillRegistry skillRegistry) {
        runtimeBindings.setSkillRegistry(skillRegistry);
    }

    public SkillRegistry getSkillRegistry() {
        return runtimeBindings.skillRegistry();
    }

    public void setSkillContextBuffer(SkillContextBuffer skillContextBuffer) {
        runtimeBindings.setSkillContextBuffer(skillContextBuffer);
    }

    public SkillContextBuffer getSkillContextBuffer() {
        return runtimeBindings.skillContextBuffer();
    }

    /**
     * 注册 write_file 写入观察者：参数 (path, [before, after])，
     * before == null 表示新建文件或读不出原文。
     * 用于把 write_file 接到行内 diff 渲染等只读副作用里；
     * 观察者抛异常不影响 write_file 主路径。
     */
    public void setWriteFileObserver(java.util.function.BiConsumer<String, String[]> observer) {
        this.writeFileObserver = observer == null ? (p, ba) -> {} : observer;
    }

    public void setLspManager(LspManager lspManager) {
        this.lspManager = lspManager == null ? new LspManager(projectPath) : lspManager;
        this.lspManager.setProjectPath(projectPath);
    }

    public LspDiagnosticReport flushPendingLspDiagnostics() {
        return lspManager == null ? LspDiagnosticReport.EMPTY : lspManager.flushPendingDiagnostics();
    }

    public SnapshotService getSnapshotService() {
        return snapshotService;
    }

    public void setSnapshotService(SnapshotService snapshotService) {
        this.snapshotService = snapshotService == null ? SnapshotService.forProject(Path.of(projectPath)) : snapshotService;
        this.customSnapshotService = snapshotService != null;
    }

    public void setToolLifecycleListener(ToolLifecycleListener listener) {
        this.toolLifecycleListener = listener == null ? ToolLifecycleListener.NO_OP : listener;
    }

    public void registerProvider(ToolProvider provider) {
        toolDefinitions.registerProvider(provider);
    }

    private void runPostEditLspHook(String displayPath, Path safePath) {
        try {
            if (lspManager != null) {
                lspManager.runPostEditLspHook(displayPath, safePath);
            }
        } catch (Exception ignored) {
            // LSP 诊断是 post-edit 辅助信号，失败不能影响工具主结果。
        }
    }

    /**
     * 获取所有工具定义（用于LLM）
     */
    public List<com.tcode.llm.LlmClient.Tool> getToolDefinitions() {
        return toolDefinitions.definitions();
    }

    /**
     * 注册一个 MCP 工具到 ToolRegistry。
     *
     * @param descriptor 工具描述（含 namespacedName 如 mcp__filesystem__read_file）
     * @param invoker    工具执行器：输入 JSON 参数字符串，输出给 LLM 看的字符串结果。
     *                   typically lambda 在内部调用 McpClient.callTool 并处理异常 → 字符串。
     */
    public synchronized void registerMcpTool(McpToolDescriptor descriptor, Function<String, String> invoker) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(invoker, "invoker");
        registerMcpToolOutput(descriptor, args -> ToolOutput.text(invoker.apply(args)));
    }

    public synchronized void registerMcpToolOutput(McpToolDescriptor descriptor, Function<String, ToolOutput> invoker) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(invoker, "invoker");
        registerMcpToolDefinition(mcpToolCatalog.register(descriptor, invoker));
    }

    private void registerMcpToolDefinition(McpToolCatalog.RegisteredTool registered) {
        McpToolDescriptor descriptor = registered.descriptor();
        toolDefinitions.register(descriptor.namespacedName(), mcpDescription(descriptor), descriptor.inputSchema(),
                args -> "MCP tools execute through the JSON invoker path");
    }

    public synchronized void unregisterMcpTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return;
        }
        mcpToolCatalog.unregister(toolName);
        toolDefinitions.remove(toolName);
    }

    public synchronized void replaceMcpToolsForServer(String serverName, List<McpToolDescriptor> newTools,
                                                      Function<McpToolDescriptor, Function<String, String>> invokerFactory) {
        replaceMcpToolOutputsForServer(serverName, newTools,
                descriptor -> args -> ToolOutput.text(invokerFactory.apply(descriptor).apply(args)));
    }

    public synchronized void replaceMcpToolOutputsForServer(String serverName, List<McpToolDescriptor> newTools,
                                                            Function<McpToolDescriptor, Function<String, ToolOutput>> invokerFactory) {
        Objects.requireNonNull(serverName, "serverName");
        Objects.requireNonNull(newTools, "newTools");
        Objects.requireNonNull(invokerFactory, "invokerFactory");
        McpToolCatalog.Replacement replacement = mcpToolCatalog.replaceForServer(serverName, newTools, invokerFactory);
        replacement.removedNames().forEach(toolDefinitions::remove);
        replacement.registeredTools().forEach(this::registerMcpToolDefinition);
    }

    /**
     * 执行工具调用
     *
     * 危险工具（write_file / execute_command / create_project）会写一行审计：
     * - 策略拦截（PathGuard / CommandGuard / 文件大小上限）→ deny
     * - 普通异常 → error
     * - 其他情况 → allow（仅表示工具调用真的发生过，工具内部的业务错误仍以返回字符串呈现给 LLM）
     */
    public String executeTool(String name, String argumentsJson) {
        return doExecuteTool(name, argumentsJson).text();
    }

    public ToolOutput executeToolOutput(String name, String argumentsJson) {
        return executeWithLifecycle(name, argumentsJson, () -> {
            if (isLegacyExecuteToolOverride()) {
                return ToolOutput.text(executeTool(name, argumentsJson));
            }
            return doExecuteTool(name, argumentsJson);
        });
    }

    protected ToolOutput executeWithLifecycle(String name, String argumentsJson,
                                              java.util.function.Supplier<ToolOutput> execution) {
        notifyToolStarted(name, argumentsJson);
        ToolOutput output = execution.get();
        recordToolTrace(name, argumentsJson, output);
        notifyToolCompleted(name, argumentsJson, output);
        return output;
    }

    private void recordToolTrace(String name, String argumentsJson, ToolOutput output) {
        toolTraceEvents.addLast(ToolTraceEvent.of(name, argumentsJson, output, output == null ? 1 : output.attempts()));
        while (toolTraceEvents.size() > 200) {
            toolTraceEvents.pollFirst();
        }
    }

    private void notifyToolStarted(String name, String argumentsJson) {
        try {
            toolLifecycleListener.onStarted(name, argumentsJson);
        } catch (Exception ignored) {
            // 生命周期事件是观察信号，不能阻断工具主路径。
        }
    }

    private void notifyToolCompleted(String name, String argumentsJson, ToolOutput output) {
        try {
            toolLifecycleListener.onCompleted(name, argumentsJson, output);
        } catch (Exception ignored) {
            // 生命周期事件是观察信号，不能阻断工具主路径。
        }
    }

    protected ToolOutput doExecuteTool(String name, String argumentsJson) {
        return executionPipeline.execute(name, argumentsJson);
    }

    private boolean isLegacyExecuteToolOverride() {
        try {
            return getClass()
                    .getMethod("executeTool", String.class, String.class)
                    .getDeclaringClass() != ToolRegistry.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    protected BrowserCheckResult checkBrowserTool(String name, String argumentsJson, boolean previewOnly) {
        return executionPipeline.checkBrowserTool(name, argumentsJson, previewOnly);
    }

    public AuditLog getAuditLog() {
        return auditLog;
    }

    public List<ToolTraceEvent> recentToolTraceEvents() {
        return List.copyOf(toolTraceEvents);
    }

    /**
     * 并行执行同一轮 LLM 返回的多个工具调用。
     *
     * 结果按传入顺序返回，调用方可以安全地按原 tool_call 顺序回灌消息历史。
     * 如果某个工具超过批次超时仍未返回，会取消任务并返回超时结果；已完成工具不受影响。
     */
    public List<ToolExecutionResult> executeTools(List<ToolInvocation> invocations) {
        return batchExecutor.execute(invocations,
                invocation -> executeToolOutput(invocation.name(), invocation.argumentsJson()));
    }

    public boolean hasTool(String name) {
        return toolDefinitions.contains(name);
    }

    private static String mcpDescription(McpToolDescriptor descriptor) {
        String base = descriptor.description() == null || descriptor.description().isBlank()
                ? "MCP server 提供的外部工具"
                : descriptor.description();
        return base + " (MCP server: " + descriptor.serverName() + ", tool: " + descriptor.name() + ")";
    }

    public record Param(String name, String type, String description, boolean required) {}

    public record Tool(String name, String description, JsonNode parameters, ToolExecutor executor) {}

    public record ToolInvocation(String id, String name, String argumentsJson) {}

    public record ToolExecutionResult(String id, String name, String argumentsJson,
                                      String result, long elapsedMillis, boolean timedOut,
                                      List<com.tcode.llm.LlmClient.ContentPart> imageParts) {
        static ToolExecutionResult completed(ToolInvocation invocation, ToolOutput output, long elapsedMillis) {
            return new ToolExecutionResult(
                    invocation.id(),
                    invocation.name(),
                    invocation.argumentsJson(),
                    resultText(output),
                    elapsedMillis,
                    output != null && output.status() == ToolCallStatus.TIMED_OUT,
                    output == null ? List.of() : output.imageParts());
        }

        private static String resultText(ToolOutput output) {
            if (output == null) {
                return "";
            }
            if (output.status() == ToolCallStatus.SUCCEEDED || output.errorCode() == ToolErrorCode.NONE) {
                return output.text();
            }
            String prefix = output.errorCode().name() + ": ";
            return output.text().startsWith(prefix) ? output.text() : prefix + output.text();
        }

        private static ToolExecutionResult completed(ToolInvocation invocation, String result, long elapsedMillis) {
            return completed(invocation, ToolOutput.text(result), elapsedMillis);
        }

        static ToolExecutionResult failed(ToolInvocation invocation, String message) {
            return completed(invocation, "工具执行失败: " + message, 0);
        }

        static ToolExecutionResult cancelled(ToolInvocation invocation) {
            return completed(invocation, "用户取消了此次工具调用", 0);
        }

        static ToolExecutionResult timedOut(ToolInvocation invocation, long timeoutSeconds) {
            return new ToolExecutionResult(
                    invocation.id(),
                    invocation.name(),
                    invocation.argumentsJson(),
                    "工具执行超时（" + timeoutSeconds + "秒），已取消",
                    timeoutSeconds * 1000,
                    true,
                    List.of()
            );
        }

        public ToolCallStatus status() {
            if (timedOut) {
                return ToolCallStatus.TIMED_OUT;
            }
            if (result != null && result.startsWith("用户取消")) {
                return ToolCallStatus.CANCELLED;
            }
            if (result != null && (result.contains("失败") || result.contains("拒绝")
                    || result.startsWith("INVALID_ARGUMENTS") || result.startsWith("UNKNOWN_TOOL")
                    || result.startsWith("INTERNAL_ERROR") || result.startsWith("POLICY_DENIED")
                    || result.startsWith("EXTERNAL_SERVICE_ERROR") || result.startsWith("MCP_SERVER_UNAVAILABLE"))) {
                return ToolCallStatus.FAILED;
            }
            return ToolCallStatus.SUCCEEDED;
        }

        public ToolErrorCode errorCode() {
            if (timedOut) {
                return ToolErrorCode.TIMEOUT;
            }
            if (result != null && result.startsWith("INVALID_ARGUMENTS")) {
                return ToolErrorCode.INVALID_ARGUMENTS;
            }
            if (result != null && result.startsWith("UNKNOWN_TOOL")) {
                return ToolErrorCode.UNKNOWN_TOOL;
            }
            if (result != null && result.startsWith("POLICY_DENIED")) {
                return ToolErrorCode.POLICY_DENIED;
            }
            if (result != null && result.startsWith("EXTERNAL_SERVICE_ERROR")) {
                return ToolErrorCode.EXTERNAL_SERVICE_ERROR;
            }
            if (result != null && result.startsWith("MCP_SERVER_UNAVAILABLE")) {
                return ToolErrorCode.MCP_SERVER_UNAVAILABLE;
            }
            if (result != null && result.startsWith("用户取消")) {
                return ToolErrorCode.CANCELLED;
            }
            if (status() == ToolCallStatus.FAILED) {
                return ToolErrorCode.INTERNAL_ERROR;
            }
            return ToolErrorCode.NONE;
        }

        public boolean retryable() {
            return timedOut;
        }

        public int attempts() {
            return 1;
        }

        public boolean hasImageParts() {
            return imageParts != null && !imageParts.isEmpty();
        }
    }

    public interface ToolExecutor {
        String execute(Map<String, String> args);
    }
}
