package com.tcode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ToolResourceScheduler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolResourceScheduler() {
    }

    static List<List<ToolRegistry.ToolInvocation>> splitIntoBatches(List<ToolRegistry.ToolInvocation> invocations) {
        if (invocations == null || invocations.isEmpty()) {
            return List.of();
        }
        List<List<ToolRegistry.ToolInvocation>> batches = new ArrayList<>();
        List<ToolRegistry.ToolInvocation> current = new ArrayList<>();
        List<ResourceLock> currentLocks = new ArrayList<>();
        for (ToolRegistry.ToolInvocation invocation : invocations) {
            List<ResourceLock> locks = inferLocks(invocation);
            if (!current.isEmpty() && conflicts(currentLocks, locks)) {
                batches.add(List.copyOf(current));
                current.clear();
                currentLocks.clear();
            }
            current.add(invocation);
            currentLocks.addAll(locks);
        }
        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }
        return List.copyOf(batches);
    }

    private static boolean conflicts(List<ResourceLock> existing, List<ResourceLock> incoming) {
        for (ResourceLock left : existing) {
            for (ResourceLock right : incoming) {
                if (left.conflictsWith(right)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<ResourceLock> inferLocks(ToolRegistry.ToolInvocation invocation) {
        String name = invocation == null ? "" : safe(invocation.name());
        JsonNode args = parseArgs(invocation == null ? null : invocation.argumentsJson());
        return switch (name) {
            case "read_file" -> List.of(ResourceLock.read("fs", path(args, "path")));
            case "write_file" -> List.of(ResourceLock.write("fs", path(args, "path")));
            case "list_dir" -> List.of(ResourceLock.read("fs", path(args, "path")));
            case "glob_files", "grep_code" -> List.of(ResourceLock.read("fs", ""));
            case "execute_command", "create_project", "revert_turn" -> List.of(ResourceLock.write("fs", ""));
            case "save_memory" -> List.of(ResourceLock.write("memory", ""));
            case "load_skill" -> List.of(ResourceLock.write("skill-context", ""));
            case "browser_connect", "browser_disconnect", "browser_status" -> List.of(ResourceLock.write("browser", ""));
            default -> inferDynamicLock(name);
        };
    }

    private static List<ResourceLock> inferDynamicLock(String name) {
        if (name.startsWith("mcp__")) {
            String[] parts = name.split("__", 3);
            String server = parts.length >= 2 ? parts[1] : "unknown";
            String category = "chrome-devtools".equals(server) ? "browser" : "mcp:" + server;
            return List.of(ResourceLock.write(category, ""));
        }
        return List.of();
    }

    private static JsonNode parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            JsonNode node = MAPPER.readTree(argumentsJson);
            return node == null || !node.isObject() ? MAPPER.createObjectNode() : node;
        } catch (Exception ignored) {
            return MAPPER.createObjectNode();
        }
    }

    private static String path(JsonNode args, String field) {
        return normalizePath(args.path(field).asText(""));
    }

    private static String normalizePath(String path) {
        String value = path == null ? "" : path.trim().replace('\\', '/');
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        while (value.contains("//")) {
            value = value.replace("//", "/");
        }
        if (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private enum LockMode {
        READ,
        WRITE
    }

    private record ResourceLock(LockMode mode, String category, String resource) {
        static ResourceLock read(String category, String resource) {
            return new ResourceLock(LockMode.READ, category, resource == null ? "" : resource);
        }

        static ResourceLock write(String category, String resource) {
            return new ResourceLock(LockMode.WRITE, category, resource == null ? "" : resource);
        }

        boolean conflictsWith(ResourceLock other) {
            if (other == null || mode == LockMode.READ && other.mode == LockMode.READ) {
                return false;
            }
            if (!category.equals(other.category)) {
                return false;
            }
            if (!"fs".equals(category)) {
                return true;
            }
            return overlaps(resource, other.resource);
        }

        private static boolean overlaps(String left, String right) {
            if (left == null || left.isBlank() || right == null || right.isBlank()) {
                return true;
            }
            return left.equals(right)
                    || left.startsWith(right + "/")
                    || right.startsWith(left + "/");
        }
    }
}
