package com.tcode.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface ToolRegistrationContext {
    ToolRegistry.Param param(String name, String type, String description, boolean required);

    JsonNode parameters(ToolRegistry.Param... params);

    void register(String name, String description, JsonNode parameters, ToolRegistry.ToolExecutor executor);
}
