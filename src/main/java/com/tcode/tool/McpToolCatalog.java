package com.tcode.tool;

import com.tcode.mcp.protocol.McpToolDescriptor;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class McpToolCatalog {
    private final ConcurrentHashMap<String, RegisteredTool> tools = new ConcurrentHashMap<>();

    public RegisteredTool register(McpToolDescriptor descriptor, Function<String, ToolOutput> invoker) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(invoker, "invoker");
        RegisteredTool registered = new RegisteredTool(descriptor, invoker);
        tools.put(descriptor.namespacedName(), registered);
        return registered;
    }

    public RegisteredTool find(String toolName) {
        return tools.get(toolName);
    }

    public boolean unregister(String toolName) {
        return toolName != null && !toolName.isBlank() && tools.remove(toolName) != null;
    }

    public Replacement replaceForServer(String serverName, List<McpToolDescriptor> newTools,
                                        Function<McpToolDescriptor, Function<String, ToolOutput>> invokerFactory) {
        Objects.requireNonNull(serverName, "serverName");
        Objects.requireNonNull(newTools, "newTools");
        Objects.requireNonNull(invokerFactory, "invokerFactory");
        String prefix = "mcp__" + serverName + "__";
        List<String> removedNames = tools.keySet().stream()
                .filter(name -> name.startsWith(prefix))
                .sorted()
                .toList();
        removedNames.forEach(tools::remove);
        List<RegisteredTool> registeredTools = newTools.stream()
                .map(descriptor -> register(descriptor, invokerFactory.apply(descriptor)))
                .toList();
        return new Replacement(removedNames, registeredTools);
    }

    public record RegisteredTool(McpToolDescriptor descriptor, Function<String, ToolOutput> invoker) {
    }

    public record Replacement(List<String> removedNames, List<RegisteredTool> registeredTools) {
    }
}
