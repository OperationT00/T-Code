package com.tcode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcode.mcp.protocol.McpToolDescriptor;
import com.tcode.policy.AuditLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionPipelineTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void executesMcpToolAndRecordsAllowAudit(@TempDir Path tempDir) throws Exception {
        ToolDefinitionCatalog tools = new ToolDefinitionCatalog();
        McpToolCatalog catalog = new McpToolCatalog();
        AuditLog auditLog = new AuditLog(tempDir.resolve("audit"));
        McpToolDescriptor descriptor = new McpToolDescriptor(
                "demo", "echo", "mcp__demo__echo", "echo",
                MAPPER.readTree("{\"type\":\"object\"}"));
        catalog.register(descriptor, args -> ToolOutput.text("echo:" + args));
        tools.register(descriptor.namespacedName(), descriptor.description(), descriptor.inputSchema(), args -> "");
        ToolExecutionPipeline pipeline = new ToolExecutionPipeline(tools, catalog, auditLog, () -> null);

        ToolOutput output = pipeline.execute("mcp__demo__echo", "{\"value\":\"x\"}");

        assertEquals("echo:{\"value\":\"x\"}", output.text());
        assertEquals(AuditLog.OUTCOME_ALLOW, auditLog.readRecent(1).get(0).outcome());
    }

    @Test
    void rejectsInvalidJsonBeforeExecutorRuns(@TempDir Path tempDir) {
        ToolDefinitionCatalog tools = new ToolDefinitionCatalog();
        AuditLog auditLog = new AuditLog(tempDir.resolve("audit"));
        tools.registerProvider(context -> context.register(
                "echo",
                "Echo",
                context.parameters(context.param("value", "string", "Value", true)),
                args -> "should-not-run"));
        ToolExecutionPipeline pipeline = new ToolExecutionPipeline(tools, new McpToolCatalog(), auditLog, () -> null);

        ToolOutput output = pipeline.execute("echo", "{bad-json");

        assertEquals(ToolCallStatus.FAILED, output.status());
        assertEquals(ToolErrorCode.INVALID_ARGUMENTS, output.errorCode());
        assertTrue(output.text().contains("INVALID_ARGUMENTS"));
    }

    @Test
    void rejectsMissingRequiredArgumentBeforeExecutorRuns(@TempDir Path tempDir) {
        ToolDefinitionCatalog tools = new ToolDefinitionCatalog();
        AuditLog auditLog = new AuditLog(tempDir.resolve("audit"));
        tools.registerProvider(context -> context.register(
                "echo",
                "Echo",
                context.parameters(context.param("value", "string", "Value", true)),
                args -> "should-not-run"));
        ToolExecutionPipeline pipeline = new ToolExecutionPipeline(tools, new McpToolCatalog(), auditLog, () -> null);

        ToolOutput output = pipeline.execute("echo", "{}");

        assertEquals(ToolCallStatus.FAILED, output.status());
        assertEquals(ToolErrorCode.INVALID_ARGUMENTS, output.errorCode());
        assertTrue(output.text().contains("value"));
    }
}
