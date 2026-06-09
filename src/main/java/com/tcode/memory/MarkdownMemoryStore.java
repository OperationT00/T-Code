package com.tcode.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MarkdownMemoryStore {
    private static final String MEMORY_DIR_PROPERTY = "tcode.memory.dir";
    private Path projectRoot;

    public MarkdownMemoryStore(Path projectRoot) {
        this.projectRoot = normalizeProjectRoot(projectRoot);
    }

    public void setProjectRoot(Path projectRoot) {
        this.projectRoot = normalizeProjectRoot(projectRoot);
    }

    public Path projectFile() {
        return projectRoot.resolve(".tcode").resolve("memory").resolve("project.md");
    }

    public Path globalFile() {
        String configured = System.getProperty(MEMORY_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize().resolve("user.md");
        }
        return Path.of(System.getProperty("user.home"), ".tcode", "memory", "user.md")
                .toAbsolutePath()
                .normalize();
    }

    public void save(String fact, MemoryScope scope) {
        String normalized = normalizeFact(fact);
        if (normalized.isEmpty()) {
            return;
        }
        Path file = fileFor(scope);
        List<String> facts = readFacts(file);
        if (facts.contains(normalized)) {
            return;
        }
        facts.add(normalized);
        writeFacts(file, facts, scope);
    }

    public Path ensureFile(MemoryScope scope) {
        Path file = fileFor(scope);
        if (!Files.exists(file)) {
            writeFacts(file, List.of(), scope);
        }
        return file;
    }

    public List<MemoryLine> listVisible() {
        List<MemoryLine> lines = new ArrayList<>();
        lines.addAll(readLines(globalFile(), MemoryScope.GLOBAL));
        lines.addAll(readLines(projectFile(), MemoryScope.PROJECT));
        return lines;
    }

    public List<MemoryLine> search(String query, int limit) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return listVisible().stream()
                .filter(line -> normalized.isBlank()
                        || line.content().toLowerCase(Locale.ROOT).contains(normalized))
                .limit(Math.max(0, limit))
                .toList();
    }

    public boolean delete(String id) {
        ParsedId parsed = parseId(id);
        if (parsed == null) {
            return false;
        }
        Path file = fileFor(parsed.scope());
        List<String> facts = readFacts(file);
        int index = parsed.lineNumber() - 1;
        if (index < 0 || index >= facts.size()) {
            return false;
        }
        facts.remove(index);
        writeFacts(file, facts, parsed.scope());
        return true;
    }

    public void clear() {
        writeFacts(projectFile(), List.of(), MemoryScope.PROJECT);
        writeFacts(globalFile(), List.of(), MemoryScope.GLOBAL);
    }

    public String buildContext(int maxTokens) {
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (MemoryLine line : listVisible()) {
            int tokens = MemoryEntry.estimateTokens(line.content());
            if (used + tokens > maxTokens) {
                break;
            }
            if (sb.isEmpty()) {
                sb.append("## Long-term Memory\n\n");
            }
            sb.append("- [").append(line.scope().label()).append("] ")
                    .append(line.content()).append("\n");
            used += tokens;
        }
        if (!sb.isEmpty()) {
            sb.append("\n");
        }
        return sb.toString();
    }

    public String statusSummary() {
        long project = readFacts(projectFile()).size();
        long global = readFacts(globalFile()).size();
        return "长期记忆: " + (project + global) + " 条 (project: " + project + ", global: " + global + ")";
    }

    private Path fileFor(MemoryScope scope) {
        return scope == MemoryScope.GLOBAL ? globalFile() : projectFile();
    }

    private List<MemoryLine> readLines(Path file, MemoryScope scope) {
        List<String> facts = readFacts(file);
        List<MemoryLine> lines = new ArrayList<>();
        for (int i = 0; i < facts.size(); i++) {
            lines.add(new MemoryLine(scope.label() + ":" + (i + 1), scope, facts.get(i)));
        }
        return lines;
    }

    private static List<String> readFacts(Path file) {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            Set<String> deduped = new LinkedHashSet<>();
            for (String line : Files.readAllLines(file)) {
                String fact = normalizeLine(line);
                if (!fact.isEmpty()) {
                    deduped.add(fact);
                }
            }
            return new ArrayList<>(deduped);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static void writeFacts(Path file, List<String> facts, MemoryScope scope) {
        try {
            Files.createDirectories(file.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("# " + (scope == MemoryScope.GLOBAL ? "User Memory" : "Project Memory"));
            lines.add("");
            for (String fact : facts) {
                String normalized = normalizeFact(fact);
                if (!normalized.isEmpty()) {
                    lines.add("- " + normalized);
                }
            }
            Files.write(file, lines);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write memory file: " + file, e);
        }
    }

    private static String normalizeLine(String line) {
        if (line == null) {
            return "";
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("- ")) {
            return normalizeFact(trimmed.substring(2));
        }
        return "";
    }

    private static String normalizeFact(String fact) {
        return fact == null ? "" : fact.trim();
    }

    private static ParsedId parseId(String id) {
        if (id == null || !id.contains(":")) {
            return null;
        }
        String[] parts = id.trim().split(":", 2);
        MemoryScope scope = MemoryScope.from(parts[0]);
        try {
            return new ParsedId(scope, Integer.parseInt(parts[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Path normalizeProjectRoot(Path projectRoot) {
        Path root = projectRoot == null ? Path.of(System.getProperty("user.dir")) : projectRoot;
        return root.toAbsolutePath().normalize();
    }

    public record MemoryLine(String id, MemoryScope scope, String content) {}

    private record ParsedId(MemoryScope scope, int lineNumber) {}
}
