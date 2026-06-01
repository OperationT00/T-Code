package com.tcode.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolDefinitionCatalogTest {

    @Test
    void registersProviderBuildsSchemaAndExportsDefinition() {
        ToolDefinitionCatalog catalog = new ToolDefinitionCatalog();

        catalog.registerProvider(context -> context.register(
                "echo",
                "Echo value",
                context.parameters(context.param("value", "string", "Value to echo", true)),
                args -> args.get("value")));

        ToolRegistry.Tool tool = catalog.find("echo");
        assertNotNull(tool);
        assertEquals("string", tool.parameters().path("properties").path("value").path("type").asText());
        assertEquals("value", tool.parameters().path("required").get(0).asText());
        assertTrue(catalog.definitions().stream().anyMatch(definition -> definition.name().equals("echo")));
        assertTrue(catalog.remove("echo"));
        assertFalse(catalog.contains("echo"));
    }
}
