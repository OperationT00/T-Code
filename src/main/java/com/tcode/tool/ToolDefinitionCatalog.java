package com.tcode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ToolDefinitionCatalog {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, ToolRegistry.Tool> tools = new ConcurrentHashMap<>();

    public void registerProvider(ToolProvider provider) {
        if (provider != null) {
            provider.register(context());
        }
    }

    public void register(String name, String description, JsonNode parameters, ToolRegistry.ToolExecutor executor) {
        tools.put(name, new ToolRegistry.Tool(name, description, parameters, executor));
    }

    public ToolRegistry.Tool find(String name) {
        return tools.get(name);
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public boolean remove(String name) {
        return tools.remove(name) != null;
    }

    public List<com.tcode.llm.LlmClient.Tool> definitions() {
        return tools.values().stream()
                .map(tool -> new com.tcode.llm.LlmClient.Tool(
                        tool.name(), tool.description(), tool.parameters()))
                .toList();
    }

    private ToolRegistrationContext context() {
        return new ToolRegistrationContext() {
            @Override
            public ToolRegistry.Param param(String name, String type, String description, boolean required) {
                return new ToolRegistry.Param(name, type, description, required);
            }

            @Override
            public JsonNode parameters(ToolRegistry.Param... params) {
                ObjectNode parameters = MAPPER.createObjectNode();
                parameters.put("type", "object");
                ObjectNode properties = parameters.putObject("properties");
                ArrayNode required = parameters.putArray("required");
                for (ToolRegistry.Param param : params) {
                    ObjectNode property = properties.putObject(param.name());
                    property.put("type", param.type());
                    property.put("description", param.description());
                    if (param.required()) {
                        required.add(param.name());
                    }
                }
                return parameters;
            }

            @Override
            public void register(String name, String description, JsonNode parameters,
                                 ToolRegistry.ToolExecutor executor) {
                ToolDefinitionCatalog.this.register(name, description, parameters, executor);
            }
        };
    }
}
