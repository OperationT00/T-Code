package com.tcode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcode.mcp.protocol.McpToolDescriptor;
import com.tcode.policy.AuditLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
