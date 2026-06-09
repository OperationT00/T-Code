# Context Memory Rag Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify t-code into a Claude Code-like architecture where code understanding uses realtime exploration, current conversation state is owned by a single context manager, and long-term memory is explicit file-based project/user knowledge.

**Architecture:** Remove RAG from the core tool and CLI surface first, then extract `conversationHistory` ownership into a `ContextManager`, then shrink `MemoryManager` into explicit long-term Markdown memory. Keep behavior changes incremental and testable after each phase.

**Tech Stack:** Java 17, Maven, JLine CLI, existing `LlmClient`, `ToolRegistry`, `PromptAssembler`, and memory/context packages.

---

## Target Architecture

```text
Code understanding:
  glob_files / grep_code / read_file / execute_command / LSP

Current turn context:
  ContextManager
    - LLM message history
    - token budget and compaction
    - tool-result summarization/truncation
    - image payload pruning
    - skill / MCP resource / local file injection boundaries

Long-term memory:
  MarkdownMemoryStore
    - project memory: ./TCODE.md or ./.tcode/memory/project.md
    - user memory: ~/.tcode/TCODE.md or ~/.tcode/memory/user.md
    - explicit save only
```

## Files To Touch

- Modify `src/main/java/com/tcode/tool/ToolRegistry.java`: remove RAG provider registration and service field.
- Delete `src/main/java/com/tcode/tool/RagToolsProvider.java`.
- Delete `src/main/java/com/tcode/tool/RagSearchService.java`.
- Delete or archive `src/main/java/com/tcode/rag/*`.
- Modify `src/main/java/com/tcode/cli/CliCommandParser.java`: remove `/index`, `/search`, `/graph` parsed command variants.
- Delete or repurpose `src/main/java/com/tcode/cli/CliCodeSearchCommandDispatcher.java`.
- Modify `src/main/java/com/tcode/cli/Main.java`: remove code-search dispatcher wiring.
- Create `src/main/java/com/tcode/context/ContextManager.java`: own LLM message history and compaction.
- Move or adapt `src/main/java/com/tcode/memory/ConversationHistoryCompactor.java` to `src/main/java/com/tcode/context/ConversationHistoryCompactor.java`.
- Modify `src/main/java/com/tcode/agent/Agent.java`: replace direct `conversationHistory` operations with `ContextManager`.
- Modify `src/main/java/com/tcode/agent/PlanExecuteAgent.java`: use shared context compaction helper or `ContextManager` for per-task histories.
- Modify `src/main/java/com/tcode/agent/SubAgent.java`: use shared context compaction helper.
- Replace `src/main/java/com/tcode/memory/MemoryManager.java` responsibilities with long-term memory only.
- Create `src/main/java/com/tcode/memory/MarkdownMemoryStore.java`.
- Create `src/main/java/com/tcode/memory/MemoryScope.java`.
- Modify `src/main/java/com/tcode/cli/CliControlCommandDispatcher.java`: keep `/memory` and `/save`, backed by Markdown memory.
- Modify prompts in `src/main/java/com/tcode/prompt/*` and agent system prompt assembly only where memory/context variables change.
- Modify docs: `AGENTS.md`, `README.md`, `docs/agents-reference.md`.
- Remove RAG tests and add context/memory replacement tests.

---

## Phase 1: Remove RAG From Core Surface

### Task 1: Remove `search_code` Tool

**Files:**
- Modify: `src/main/java/com/tcode/tool/ToolRegistry.java`
- Delete: `src/main/java/com/tcode/tool/RagToolsProvider.java`
- Delete: `src/main/java/com/tcode/tool/RagSearchService.java`
- Test: `src/test/java/com/tcode/tool/ToolRegistryTest.java`

- [x] **Step 1: Write failing test**

Update `ToolRegistryTest` to assert that `search_code` is no longer exported:

```java
@Test
void toolDefinitionsDoNotExposeSearchCode() {
    ToolRegistry registry = new ToolRegistry();

    boolean hasSearchCode = registry.getToolDefinitions().stream()
            .anyMatch(tool -> "search_code".equals(tool.function().name()));

    assertFalse(hasSearchCode);
}
```

- [x] **Step 2: Run targeted test**

Run:

```bash
mvn test -Dtest=ToolRegistryTest -DskipTests=false
```

Expected before implementation: fail if `search_code` is still registered.

- [x] **Step 3: Remove provider registration**

In `ToolRegistry`, delete the `RagSearchService` field and remove:

```java
registerProvider(new RagToolsProvider(ragSearchService::search));
```

Then delete the two RAG tool service/provider files.

- [x] **Step 4: Run targeted test**

Run:

```bash
mvn test -Dtest=ToolRegistryTest -DskipTests=false
```

Expected: pass.

### Task 2: Remove RAG CLI Commands

**Files:**
- Modify: `src/main/java/com/tcode/cli/CliCommandParser.java`
- Modify: `src/main/java/com/tcode/cli/Main.java`
- Delete: `src/main/java/com/tcode/cli/CliCodeSearchCommandDispatcher.java`
- Test: `src/test/java/com/tcode/cli/CliCommandParserTest.java`

- [x] **Step 1: Update parser tests**

Remove tests that expect `/index`, `/search`, and `/graph` command types. Add tests that these commands become unknown:

```java
@Test
void removedRagCommandsParseAsUnknown() {
    assertEquals(CliCommandParser.CommandType.UNKNOWN,
            CliCommandParser.parse("/index").type());
    assertEquals(CliCommandParser.CommandType.UNKNOWN,
            CliCommandParser.parse("/search login").type());
    assertEquals(CliCommandParser.CommandType.UNKNOWN,
            CliCommandParser.parse("/graph Agent").type());
}
```

- [x] **Step 2: Remove parser branches and dispatcher wiring**

Delete `INDEX_CODE`, `SEARCH_CODE`, and `GRAPH_QUERY` command types and their parse branches. Remove `CliCodeSearchCommandDispatcher.dispatch(...)` from `Main`.

- [x] **Step 3: Run parser tests**

Run:

```bash
mvn test -Dtest=CliCommandParserTest -DskipTests=false
```

Expected: pass.

### Task 3: Delete RAG Package And Tests

**Files:**
- Delete: `src/main/java/com/tcode/rag/*`
- Delete tests: `src/test/java/com/tcode/rag/*`
- Modify: `pom.xml` only if RAG-only dependencies become unused.

- [x] **Step 1: Remove RAG source and tests**

Delete the package after all compile references are gone.

- [x] **Step 2: Check dependency usage**

Search for embedding/vector dependencies. Remove only dependencies that are no longer referenced anywhere else.

Run:

```bash
rg -n "EmbeddingClient|VectorStore|CodeIndex|CodeRetriever|sqlite|sqlite-jdbc|embedding" pom.xml src
```

- [x] **Step 3: Compile**

Run:

```bash
mvn test -DskipTests=false -Dtest=ToolRegistryTest,CliCommandParserTest
```

Expected: pass.

---

## Phase 2: Extract ContextManager

### Task 4: Introduce ContextManager Shell

**Files:**
- Create: `src/main/java/com/tcode/context/ContextManager.java`
- Move/adapt: `src/main/java/com/tcode/memory/ConversationHistoryCompactor.java` to `src/main/java/com/tcode/context/ConversationHistoryCompactor.java`
- Test: `src/test/java/com/tcode/context/ContextManagerTest.java`

- [x] **Step 1: Add tests for message ownership**

Create tests for:

```java
@Test
void startsWithSystemPromptAndAppendsUserAssistantAndToolMessages() {
    ContextManager manager = new ContextManager(null, ContextProfile.custom(8000, 4000));
    manager.setSystemPrompt("system");
    manager.addUserMessage(LlmClient.Message.user("hello"));
    manager.addAssistantMessage(LlmClient.Message.assistant("hi"));
    manager.addToolMessage("tool-id", "result");

    List<LlmClient.Message> messages = manager.messages();

    assertEquals("system", messages.get(0).role());
    assertEquals("user", messages.get(1).role());
    assertEquals("assistant", messages.get(2).role());
    assertEquals("tool", messages.get(3).role());
}
```

- [x] **Step 2: Implement minimal ContextManager**

Implement methods:

```java
public final class ContextManager {
    private final List<LlmClient.Message> messages = new ArrayList<>();
    private final ConversationHistoryCompactor compactor;
    private ContextProfile contextProfile;

    public void setSystemPrompt(String prompt) { ... }
    public void addUserMessage(LlmClient.Message message) { ... }
    public void addAssistantMessage(LlmClient.Message message) { ... }
    public void addToolMessage(String toolCallId, String result) { ... }
    public List<LlmClient.Message> messages() { return new ArrayList<>(messages); }
    public void compactIfNeeded(PrintStream out) { ... }
    public void clearKeepingSystem() { ... }
}
```

- [x] **Step 3: Run context tests**

Run:

```bash
mvn test -Dtest=ContextManagerTest,ConversationHistoryCompactorTest -DskipTests=false
```

Expected: pass.

### Task 5: Migrate Agent To ContextManager

**Files:**
- Modify: `src/main/java/com/tcode/agent/Agent.java`
- Test: `src/test/java/com/tcode/agent/AgentMemoryHintTest.java`
- Test: existing Agent-related tests.

- [x] **Step 1: Replace field**

Replace:

```java
private final List<LlmClient.Message> conversationHistory;
private final ConversationHistoryCompactor historyCompactor;
```

with:

```java
private final ContextManager contextManager;
```

- [x] **Step 2: Route all message mutations through ContextManager**

Convert direct calls:

```java
conversationHistory.add(...)
conversationHistory.set(0, ...)
conversationHistory.clear()
```

to context methods.

- [x] **Step 3: Keep public behavior stable**

Keep `getConversationHistory()` returning a copy from `contextManager.messages()` so tests and callers remain compatible during transition.

- [x] **Step 4: Run Agent tests**

Run:

```bash
mvn test -Dtest=AgentMemoryHintTest,PlanExecuteAgentTest -DskipTests=false
```

Expected: pass.

### Task 6: Migrate PlanExecuteAgent And SubAgent Compaction

**Files:**
- Modify: `src/main/java/com/tcode/agent/PlanExecuteAgent.java`
- Modify: `src/main/java/com/tcode/agent/SubAgent.java`
- Test: `src/test/java/com/tcode/agent/PlanExecuteAgentTest.java`
- Test: `src/test/java/com/tcode/agent/AgentOrchestratorTest.java`

- [x] **Step 1: Import context compactor**

Change imports from `com.tcode.memory.ConversationHistoryCompactor` to `com.tcode.context.ConversationHistoryCompactor`.

- [x] **Step 2: Use shared helper**

Keep local per-task message lists in `PlanExecuteAgent`, but call the context package compactor. Do not introduce shared mutable context across parallel subagents.

- [x] **Step 3: Run tests**

Run:

```bash
mvn test -Dtest=PlanExecuteAgentTest,AgentOrchestratorTest -DskipTests=false
```

Expected: pass.

---

## Phase 3: Simplify Memory To Explicit Markdown Long-Term Memory

### Task 7: Add MarkdownMemoryStore

**Files:**
- Create: `src/main/java/com/tcode/memory/MarkdownMemoryStore.java`
- Create: `src/main/java/com/tcode/memory/MemoryScope.java`
- Test: `src/test/java/com/tcode/memory/MarkdownMemoryStoreTest.java`

- [x] **Step 1: Define scope enum**

```java
public enum MemoryScope {
    PROJECT,
    GLOBAL
}
```

- [x] **Step 2: Add store tests**

Test that saving project/global facts appends bullet lines to the correct files and loading returns both global and project memory.

- [x] **Step 3: Implement store**

Rules:

- Project memory defaults to `<projectRoot>/.tcode/memory/project.md`.
- Global memory defaults to `~/.tcode/memory/user.md`.
- Saving appends `- fact`.
- Loading reads files if they exist; missing files return empty content.
- Duplicate exact bullet lines are ignored.

- [x] **Step 4: Run tests**

Run:

```bash
mvn test -Dtest=MarkdownMemoryStoreTest -DskipTests=false
```

Expected: pass.

### Task 8: Shrink MemoryManager

**Files:**
- Modify: `src/main/java/com/tcode/memory/MemoryManager.java`
- Delete or deprecate: `ConversationMemory`, `ContextCompressor`, `MemoryRetriever` after references are gone.
- Test: `src/test/java/com/tcode/memory/MemoryManagerTest.java`

- [x] **Step 1: Redefine MemoryManager responsibility**

`MemoryManager` should keep:

```java
public void setProjectPath(String projectPath)
public void storeFact(String fact)
public void storeFact(String fact, String scope)
public String buildMemoryContext()
public String getSystemStatus()
public List<String> listProjectMemory()
public List<String> listGlobalMemory()
public boolean deleteMemoryLine(String scope, int lineNumber)
public void clearLongTerm()
```

Remove short-term APIs from production callers:

```java
addUserMessage
addAssistantMessage
addToolResult
compressIfNeeded
getShortTermMemory
retrieveRelevant
```

- [x] **Step 2: Update Agent usage**

Remove calls that write current conversation into `MemoryManager`. Agent should only ask:

```java
String memoryContext = memoryManager.buildMemoryContext();
```

then inject that into the system prompt.

- [x] **Step 3: Run memory tests**

Run:

```bash
mvn test -Dtest=MemoryManagerTest,MarkdownMemoryStoreTest -DskipTests=false
```

Expected: pass.

### Task 9: Update CLI Memory Commands

**Files:**
- Modify: `src/main/java/com/tcode/cli/CliControlCommandDispatcher.java`
- Modify: `src/main/java/com/tcode/tui/TuiSessionController.java` if TUI still exposes memory commands.
- Test: `src/test/java/com/tcode/cli/CliCommandParserTest.java`

- [x] **Step 1: Keep command names stable**

Keep:

```text
/memory
/memory list
/memory search <keyword>
/memory delete <scope>:<line>
/memory clear
/save <fact>
/save --global <fact>
```

- [x] **Step 2: Adjust output semantics**

`/memory list` should show Markdown file paths and bullet lines. `/memory search` can be simple case-insensitive substring search over loaded Markdown lines.

- [x] **Step 3: Run CLI tests**

Run:

```bash
mvn test -Dtest=CliCommandParserTest -DskipTests=false
```

Expected: pass.

---

## Phase 4: Prompt And Documentation Cleanup

### Task 10: Update Prompt Contract

**Files:**
- Modify: `src/main/java/com/tcode/prompt/PromptContext.java`
- Modify: `src/main/java/com/tcode/prompt/PromptAssembler.java`
- Modify: Agent prompt call sites.
- Test: `src/test/java/com/tcode/prompt/PromptAssemblerTest.java`

- [x] **Step 1: Rename memory context if helpful**

Keep `memoryContext` if minimal churn is preferred. It should now contain Markdown memory content, not retrieved memory entries.

- [x] **Step 2: Ensure prompt separates memory and current context**

Prompt sections should express:

```text
Long-term memory:
  project/user rules and facts

Current task context:
  files, MCP resources, skill bodies, LSP diagnostics
```

- [x] **Step 3: Run prompt tests**

Run:

```bash
mvn test -Dtest=PromptAssemblerTest -DskipTests=false
```

Expected: pass.

### Task 11: Update Docs

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `docs/agents-reference.md`
- Delete or rewrite: RAG journey docs if they are no longer true.

- [x] **Step 1: Remove RAG as delivered core capability**

Delete references to `/index`, `/search`, `search_code`, `rag/`, `EmbeddingClient`, `VectorStore`, and RAG tests from active instructions.

- [x] **Step 2: Add new architecture statement**

Add:

```text
Code understanding uses realtime exploration: glob_files, grep_code, read_file, execute_command, and LSP diagnostics. RAG is not part of the core runtime.

Memory is explicit long-term Markdown memory only. Current conversation state is managed by ContextManager.
```

- [x] **Step 3: Run documentation grep**

Run:

```bash
rg -n "RAG|/index|/search|search_code|CodeIndex|VectorStore|ConversationMemory|shortTermMemory" AGENTS.md README.md docs src/main/java
```

Expected: only historical docs or intentionally retained migration notes remain.

---

## Phase 5: Final Regression

### Task 12: Run Focused And Quick Test Suites

**Files:**
- No source files.

- [x] **Step 1: Run command parsing and tool tests**

```bash
mvn test -Dtest=CliCommandParserTest,ToolRegistryTest -DskipTests=false
```

- [x] **Step 2: Run context and memory tests**

```bash
mvn test -Dtest=ContextManagerTest,ConversationHistoryCompactorTest,MemoryManagerTest,MarkdownMemoryStoreTest -DskipTests=false
```

- [x] **Step 3: Run agent tests**

```bash
mvn test -Dtest=AgentMemoryHintTest,PlanExecuteAgentTest,AgentOrchestratorTest -DskipTests=false
```

- [x] **Step 4: Run quick regression**

```bash
mvn test -Pquick
```

Expected: pass.

---

## Risk Notes

- Remove RAG first because it is mostly independent and immediately simplifies the tool surface.
- Do not remove `conversationHistory` behavior before `ContextManager` has compatibility methods; Agent tests should stay useful through the migration.
- Do not introduce automatic memory saving during this refactor. Keep long-term memory explicit.
- Keep Markdown memory format deliberately simple first. Add frontmatter, tags, or LLM memory selection later only if usage proves the need.
- If deleting RAG docs feels too destructive, move them under a historical journey section and mark them as removed from core runtime.

## Execution Options

1. Subagent-driven execution: one task per worker with review between phases.
2. Inline execution: implement Phase 1 first, verify, then proceed phase by phase.

Recommended first milestone: complete Phase 1 only, because removing RAG is low-risk and gives immediate clarity before touching memory/context internals.
