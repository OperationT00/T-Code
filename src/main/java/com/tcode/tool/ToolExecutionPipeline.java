package com.tcode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.tcode.browser.BrowserAuditMetadata;
import com.tcode.browser.BrowserCheckResult;
import com.tcode.browser.BrowserGuard;
import com.tcode.policy.AuditLog;
import com.tcode.policy.PolicyException;
import com.tcode.runtime.CancellationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ToolExecutionPipeline {
    private static final Set<String> AUDIT_TOOLS =
            Set.of("write_file", "execute_command", "create_project", "revert_turn");

    private final ToolDefinitionCatalog toolDefinitions;
    private final McpToolCatalog mcpToolCatalog;
    private final AuditLog auditLog;
    private final Supplier<BrowserGuard> browserGuardSupplier;

    public ToolExecutionPipeline(ToolDefinitionCatalog toolDefinitions,
                                 McpToolCatalog mcpToolCatalog,
                                 AuditLog auditLog,
                                 Supplier<BrowserGuard> browserGuardSupplier) {
        this.toolDefinitions = toolDefinitions;
        this.mcpToolCatalog = mcpToolCatalog;
        this.auditLog = auditLog;
        this.browserGuardSupplier = browserGuardSupplier;
    }

    public ToolOutput execute(String name, String argumentsJson) {
        if (CancellationContext.isCancelled()) {
            return new ToolOutput("用户取消了此次工具调用",
                    List.of(), ToolCallStatus.CANCELLED, ToolErrorCode.CANCELLED, false, 0, 1);
        }
        ToolRegistry.Tool tool = toolDefinitions.find(name);
        if (tool == null) {
            return ToolOutput.failure("UNKNOWN_TOOL: 未知工具: " + name,
                    ToolErrorCode.UNKNOWN_TOOL, false);
        }

        boolean audit = shouldAudit(name);
        long startedAt = System.nanoTime();
        BrowserAuditMetadata metadata = null;
        try {
            ToolArgumentValidator.ValidationResult validation =
                    ToolArgumentValidator.validate(tool.parameters(), argumentsJson);
            if (!validation.valid()) {
                return ToolOutput.failure("INVALID_ARGUMENTS: " + validation.errorMessage(),
                                ToolErrorCode.INVALID_ARGUMENTS, false)
                        .withTiming(elapsedMillis(startedAt));
            }

            McpToolCatalog.RegisteredTool mcpTool = mcpToolCatalog.find(name);
            if (mcpTool != null) {
                BrowserCheckResult browserCheck = checkBrowserTool(name, argumentsJson, false);
                metadata = browserCheck.metadata();
                if (browserCheck.blocked()) {
                    throw new PolicyException(browserCheck.reason());
                }
                ToolOutput output = mcpTool.invoker().apply(argumentsJson);
                if (output == null) {
                    output = ToolOutput.text("");
                }
                BrowserGuard browserGuard = browserGuardSupplier.get();
                if (browserGuard != null) {
                    browserGuard.applyAfterExecution(name, argumentsJson, output.text());
                }
                recordAllow(audit, name, argumentsJson, startedAt, metadata);
                return output.withTiming(elapsedMillis(startedAt));
            }

            JsonNode args = validation.arguments();
            Map<String, String> argMap = new HashMap<>();
            args.fields().forEachRemaining(entry -> argMap.put(entry.getKey(), entry.getValue().asText()));
            ToolOutput output = ToolOutput.text(tool.executor().execute(argMap));
            recordAllow(audit, name, argumentsJson, startedAt, metadata);
            return output.withTiming(elapsedMillis(startedAt));
        } catch (PolicyException e) {
            if (audit) {
                auditLog.record(AuditLog.AuditEntry.denyByPolicy(
                        name, argumentsJson, e.getMessage(), elapsedMillis(startedAt), metadata));
            }
            return ToolOutput.denied("POLICY_DENIED: 策略拒绝: " + e.getMessage(),
                    ToolErrorCode.POLICY_DENIED).withTiming(elapsedMillis(startedAt));
        } catch (Exception e) {
            if (audit) {
                auditLog.record(AuditLog.AuditEntry.error(
                        name, argumentsJson, e.getMessage(), elapsedMillis(startedAt), metadata));
            }
            return ToolOutput.failure("INTERNAL_ERROR: 工具执行失败: " + e.getMessage(),
                    ToolErrorCode.INTERNAL_ERROR, false).withTiming(elapsedMillis(startedAt));
        }
    }

    public BrowserCheckResult checkBrowserTool(String name, String argumentsJson, boolean previewOnly) {
        BrowserGuard browserGuard = browserGuardSupplier.get();
        if (browserGuard == null || !BrowserGuard.isChromeTool(name)) {
            return BrowserCheckResult.allow(null);
        }
        return browserGuard.check(name, argumentsJson, !previewOnly);
    }

    private void recordAllow(boolean audit, String name, String argumentsJson, long startedAt,
                             BrowserAuditMetadata metadata) {
        if (audit) {
            auditLog.record(AuditLog.AuditEntry.allow(name, argumentsJson, elapsedMillis(startedAt), metadata));
        }
    }

    private static boolean shouldAudit(String name) {
        return AUDIT_TOOLS.contains(name) || (name != null && name.startsWith("mcp__"));
    }

    private static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
