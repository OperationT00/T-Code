package com.tcode.memory;

import com.tcode.context.ContextProfile;
import com.tcode.llm.LlmClient;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Long-term memory facade. Current conversation state belongs to ContextManager.
 */
public class MemoryManager {
    private final MarkdownMemoryStore markdownStore;
    private TokenBudget tokenBudget;
    private ContextProfile contextProfile;
    private String currentProject;

    public MemoryManager(LlmClient llmClient) {
        this(llmClient, ContextProfile.from(llmClient));
    }

    public MemoryManager(LlmClient llmClient, int shortTermBudget, int contextWindow) {
        this(llmClient, ContextProfile.custom(contextWindow, shortTermBudget));
    }

    private MemoryManager(LlmClient llmClient, ContextProfile contextProfile) {
        this.contextProfile = contextProfile;
        this.tokenBudget = new TokenBudget(contextProfile.maxContextWindow());
        this.currentProject = defaultProjectKey();
        this.markdownStore = new MarkdownMemoryStore(Path.of(currentProject));
    }

    public void setLlmClient(LlmClient llmClient) {
        applyContextProfile(ContextProfile.from(llmClient));
    }

    public void applyContextProfile(ContextProfile contextProfile) {
        if (contextProfile == null) {
            return;
        }
        this.contextProfile = contextProfile;
        this.tokenBudget = new TokenBudget(contextProfile.maxContextWindow());
    }

    public void setProjectPath(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return;
        }
        this.currentProject = normalizeProjectKey(projectPath);
        this.markdownStore.setProjectRoot(Path.of(currentProject));
    }

    public void addUserMessage(String content) {
        // Current conversation state is managed by ContextManager.
    }

    public void addAssistantMessage(String content) {
        // Current conversation state is managed by ContextManager.
    }

    public void addToolResult(String toolName, String result) {
        // Current conversation state is managed by ContextManager.
    }

    public void storeFact(String fact) {
        storeFact(fact, "project");
    }

    public void storeFact(String fact, String scope) {
        markdownStore.save(fact, MemoryScope.from(scope));
    }

    public List<MemoryEntry> retrieveRelevant(String query, int limit) {
        return searchLongTerm(query, limit);
    }

    public List<MemoryEntry> listLongTerm() {
        return markdownStore.listVisible().stream()
                .map(this::toEntry)
                .toList();
    }

    public List<MemoryEntry> searchLongTerm(String query, int limit) {
        return markdownStore.search(query, limit).stream()
                .map(this::toEntry)
                .toList();
    }

    public boolean deleteLongTerm(String id) {
        return markdownStore.delete(id);
    }

    public String buildContextForQuery(String query, int maxTokens) {
        return markdownStore.buildContext(maxTokens);
    }

    public String buildMemoryContext() {
        return markdownStore.buildContext(contextProfile.memoryContextTokens());
    }

    public void recordTokenUsage(int inputTokens, int outputTokens) {
        tokenBudget.recordUsage(inputTokens, outputTokens);
    }

    public void recordTokenUsage(int inputTokens, int outputTokens, int cachedInputTokens) {
        tokenBudget.recordUsage(inputTokens, outputTokens, cachedInputTokens);
    }

    public boolean compressIfNeeded() {
        return false;
    }

    public void clearShortTerm() {
        // Current conversation state is managed by ContextManager.
    }

    public void clearLongTerm() {
        markdownStore.clear();
    }

    public Path ensureMemoryFile(String scope) {
        return markdownStore.ensureFile(MemoryScope.from(scope));
    }

    public String getSystemStatus() {
        return "上下文策略: " + contextProfile.summary() + "\n" +
                markdownStore.statusSummary() + "\n" +
                tokenBudget.getUsageReport();
    }

    public TokenBudget getTokenBudget() {
        return tokenBudget;
    }

    public ContextProfile getContextProfile() {
        return contextProfile;
    }

    public String getCurrentProject() {
        return currentProject;
    }

    public MarkdownMemoryStore getMarkdownStore() {
        return markdownStore;
    }

    private MemoryEntry toEntry(MarkdownMemoryStore.MemoryLine line) {
        return new MemoryEntry(
                line.id(),
                line.content(),
                MemoryEntry.MemoryType.FACT,
                Map.of("source", "markdown", "scope", line.scope().label(),
                        "project", currentProject),
                MemoryEntry.estimateTokens(line.content())
        );
    }

    private static String defaultProjectKey() {
        return normalizeProjectKey(System.getProperty("user.dir"));
    }

    private static String normalizeProjectKey(String path) {
        try {
            Path candidate = Path.of(path).toAbsolutePath().normalize();
            if (java.nio.file.Files.exists(candidate)) {
                return displayPath(candidate.toRealPath());
            }
            return displayPath(candidate);
        } catch (Exception e) {
            return displayPath(Path.of(path).toAbsolutePath().normalize());
        }
    }

    private static String displayPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
