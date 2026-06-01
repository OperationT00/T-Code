package com.tcode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcode.mcp.protocol.McpToolDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McpToolCatalogTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void registersFindsAndUnregistersTools() throws Exception {
        McpToolCatalog catalog = new McpToolCatalog();
        McpToolDescriptor descriptor = descriptor("demo", "echo");

        catalog.register(descriptor, args -> ToolOutput.text("echo:" + args));

        assertEquals("echo:{}", catalog.find("mcp__demo__echo").invoker().apply("{}").text());
        assertTrue(catalog.unregister("mcp__demo__echo"));
        assertNull(catalog.find("mcp__demo__echo"));
    }

    @Test
    void replacesOnlyToolsFromRequestedServer() throws Exception {
        McpToolCatalog catalog = new McpToolCatalog();
        catalog.register(descriptor("demo", "old"), args -> ToolOutput.text("old"));
        catalog.register(descriptor("other", "keep"), args -> ToolOutput.text("keep"));

        McpToolCatalog.Replacement replacement = catalog.replaceForServer(
                "demo", List.of(descriptor("demo", "next")), tool -> args -> ToolOutput.text(tool.name()));

        assertEquals(List.of("mcp__demo__old"), replacement.removedNames());
        assertNull(catalog.find("mcp__demo__old"));
        assertNotNull(catalog.find("mcp__demo__next"));
        assertNotNull(catalog.find("mcp__other__keep"));
    }

    private static McpToolDescriptor descriptor(String server, String name) throws Exception {
        return new McpToolDescriptor(server, name, McpToolDescriptor.namespaced(server, name),
                name, MAPPER.readTree("{\"type\":\"object\"}"));
    }
}
