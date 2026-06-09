# Context Management Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade t-code context management from a single 90% compaction trigger into a pressure-aware, tool-aware, auditable context system for long coding-agent tasks.

**Architecture:** Keep `ContextManager` as the owner of active LLM messages. Add pressure levels, structured tool result summarization, manual compaction, original event storage, and optional async pre-compaction as independent layers. The active context can be summarized, but raw context events remain recoverable.

**Tech Stack:** Java 17, Maven, existing `LlmClient`, `ContextManager`, `ConversationHistoryCompactor`, CLI command dispatchers, and JUnit 5 tests.

---

## Target Design

```text
ContextManager
  +- activeMessages
  |  +- current LLM input, can be summarized or compacted
  +- ContextPressure
  |  +- NORMAL / CONSERVE / COMPACT / CRITICAL
  +- ToolResultSummarizer
  |  +- ReadFileToolResultSummarizer
  |  +- GrepCodeToolResultSummarizer
  |  +- ExecuteCommandToolResultSummarizer
  |  +- DefaultHeadTailToolResultSummarizer
  +- ContextEventStore
  |  +- lossless JSONL raw user / assistant / tool events
  +- ConversationHistoryCompactor
     +- automatic compaction
     +- manual /compact [focus]
     +- optional async pre-compaction
```

## Rollout Order

1. Add pressure-level observability without behavior changes.
2. Add pressure-aware default fallback summaries. This still uses head/tail, but only as the universal fallback for unknown tools.
3. Replace summaries for high-value tools with deterministic structured summaries: `execute_command`, `grep_code`, and `read_file`.
4. Add manual `/compact [focus]` so users can compact early and steer what the summary preserves.
5. Add lossless `ContextEventStore`; first recall version is read-only display, not automatic context injection.
6. Add explicit inject later only if recall display proves useful.
7. Add async pre-compaction after synchronous behavior is stable.

## Problems Solved by Each Step

| Step | What changes | Problem solved |
|------|--------------|----------------|
| Pressure levels | Introduce `NORMAL / CONSERVE / COMPACT / CRITICAL` from token usage | Replaces one late 90% trigger with earlier risk signals |
| Pressure-aware fallback summaries | Default head/tail limits tighten as pressure rises | Prevents unknown or low-value tools from suddenly flooding context |
| Structured tool summaries | Core tools use tool-specific deterministic summaries | Preserves coding-relevant facts that simple head/tail can miss |
| `/compact [focus]` | User can compact manually and provide summary focus | Avoids waiting for auto-compaction and reduces loss of important details |
| Event store | Raw user / assistant / tool content is stored in JSONL | Makes compression reversible from a human/debugging perspective |
| Recall display | `/context recall` and `/context show` display matched events | Lets users choose what matters without polluting active context |
| Explicit inject | Optional future `/context inject <event_id>` | Allows deliberate rehydration after the recall flow is proven safe |
| Async pre-compaction | Background summary snapshot, sync fallback at critical pressure | Reduces blocking LLM summary calls during long tasks |

> Important: head/tail is not the final summary strategy. It remains as the default fallback for unknown tools and MCP dynamic tools. High-frequency coding tools should use structured summarizers.

---

## Phase 1: Context Pressure Levels

### Task 1: Add `ContextPressureLevel`

**Files:**
- Create: `src/main/java/com/tcode/context/ContextPressureLevel.java`
- Modify: `src/main/java/com/tcode/context/ContextManager.java`
- Test: `src/test/java/com/tcode/context/ContextManagerTest.java`

- [x] **Step 1: Write failing tests**

Add tests for pressure levels:

```java
@Test
void pressureLevelChangesWithEstimatedTokens() {
    ContextProfile profile = ContextProfile.custom(10_000, 4_000);

    assertEquals(ContextPressureLevel.NORMAL,
            ContextPressureLevel.fromUsage(6_999, profile.maxContextWindow()));
    assertEquals(ContextPressureLevel.CONSERVE,
            ContextPressureLevel.fromUsage(7_000, profile.maxContextWindow()));
    assertEquals(ContextPressureLevel.COMPACT,
            ContextPressureLevel.fromUsage(8_500, profile.maxContextWindow()));
    assertEquals(ContextPressureLevel.CRITICAL,
            ContextPressureLevel.fromUsage(9_500, profile.maxContextWindow()));
}
```

- [x] **Step 2: Run test to verify failure**

```bash
mvn test -Dtest=ContextManagerTest -DskipTests=false
```

Expected: compile failure because `ContextPressureLevel` does not exist.

- [x] **Step 3: Implement enum**

```java
package com.tcode.context;

public enum ContextPressureLevel {
    NORMAL,
    CONSERVE,
    COMPACT,
    CRITICAL;

    public static ContextPressureLevel fromUsage(int estimatedTokens, int maxContextWindow) {
        if (maxContextWindow <= 0) {
            return NORMAL;
        }
        double ratio = estimatedTokens / (double) maxContextWindow;
        if (ratio >= 0.95) return CRITICAL;
        if (ratio >= 0.85) return COMPACT;
        if (ratio >= 0.70) return CONSERVE;
        return NORMAL;
    }
}
```

- [x] **Step 4: Expose pressure in `ContextManager`**

Add:

```java
public ContextPressureLevel pressureLevel() {
    return ContextPressureLevel.fromUsage(
            com.tcode.memory.TokenBudget.estimateMessagesTokens(messages),
            contextProfile.maxContextWindow());
}
```

- [x] **Step 5: Run tests**

```bash
mvn test -Dtest=ContextManagerTest -DskipTests=false
```

Expected: pass.

### Task 2: Show Pressure in `/context`

**Files:**
- Modify: `src/main/java/com/tcode/agent/Agent.java`
- Test: `src/test/java/com/tcode/agent/AgentContextStatusTest.java`

- [x] **Step 1: Add failing test**

Assert `/context` status includes:

```text
Context pressure:
```

- [x] **Step 2: Update status output**

In `Agent.getContextStatus()`, add:

```java
sb.append("    Context pressure:          ")
        .append(contextManager.pressureLevel())
        .append("\n");
```

- [x] **Step 3: Run tests**

```bash
mvn test -Dtest=AgentContextStatusTest -DskipTests=false
```

Expected: pass.

---

## Phase 2: Pressure-Aware Default Fallback Summaries

This phase deliberately keeps head/tail behavior because it is the fallback layer. It does not replace Phase 3. After Phase 3 lands, the dispatch order is:

```text
execute_command -> ExecuteCommandToolResultSummarizer
grep_code       -> GrepCodeToolResultSummarizer
read_file       -> ReadFileToolResultSummarizer
unknown tools   -> DefaultHeadTailToolResultSummarizer
```

This separation makes the rollout safer: first connect pressure policy to the summary pipeline, then improve high-value tools one by one.

### Task 3: Add Summary Policy

**Files:**
- Create: `src/main/java/com/tcode/context/ToolSummaryPolicy.java`
- Modify: `src/main/java/com/tcode/context/ContextManager.java`
- Test: `src/test/java/com/tcode/context/ContextManagerTest.java`

- [x] **Step 1: Write failing tests**

```java
@Test
void toolSummaryPolicyTightensUnderPressure() {
    assertEquals(4000, ToolSummaryPolicy.forLevel(ContextPressureLevel.NORMAL).maxChars());
    assertEquals(2500, ToolSummaryPolicy.forLevel(ContextPressureLevel.CONSERVE).maxChars());
    assertEquals(1600, ToolSummaryPolicy.forLevel(ContextPressureLevel.COMPACT).maxChars());
    assertEquals(800, ToolSummaryPolicy.forLevel(ContextPressureLevel.CRITICAL).maxChars());
}
```

- [x] **Step 2: Implement policy**

```java
package com.tcode.context;

public record ToolSummaryPolicy(int maxChars, int edgeChars) {
    public static ToolSummaryPolicy forLevel(ContextPressureLevel level) {
        return switch (level == null ? ContextPressureLevel.NORMAL : level) {
            case NORMAL -> new ToolSummaryPolicy(4_000, 1_200);
            case CONSERVE -> new ToolSummaryPolicy(2_500, 800);
            case COMPACT -> new ToolSummaryPolicy(1_600, 500);
            case CRITICAL -> new ToolSummaryPolicy(800, 250);
        };
    }
}
```

- [x] **Step 3: Apply policy in `ContextManager`**

Change `summarizeToolResult(toolName, result)` to accept a policy:

```java
public static String summarizeToolResult(String toolName, String result, ToolSummaryPolicy policy) {
    ToolSummaryPolicy activePolicy = policy == null
            ? ToolSummaryPolicy.forLevel(ContextPressureLevel.NORMAL)
            : policy;
    if (result == null || result.length() <= activePolicy.maxChars()) {
        return result;
    }
    int edge = activePolicy.edgeChars();
    int omitted = Math.max(0, result.length() - edge * 2);
    return """
            [Tool result summarized: original %,d chars, omitted %,d chars]
            Tool: %s

            --- head ---
            %s

            --- tail ---
            %s
            """.formatted(
            result.length(),
            omitted,
            toolName == null || toolName.isBlank() ? "unknown" : toolName,
            result.substring(0, Math.min(edge, result.length())),
            result.substring(Math.max(0, result.length() - edge))
    ).trim();
}
```

Use it from `addToolMessage`:

```java
String stored = summarizeToolResult(toolName, result,
        ToolSummaryPolicy.forLevel(pressureLevel()));
```

- [x] **Step 4: Run tests**

```bash
mvn test -Dtest=ContextManagerTest -DskipTests=false
```

Expected: pass.

---

## Phase 3: Tool-Specific Structured Summaries

This phase changes the quality of summaries. It should not call an LLM. Summaries must be deterministic, cheap, and testable.

Target behavior:

```text
execute_command:
  keep command metadata when available, exit code, stderr, failure keywords, stack-trace anchors, and tail

grep_code:
  keep file paths, line numbers, matched lines, match counts, and omitted counts

read_file:
  keep file path, line range, package/import/class/method structure lines, and selected head/tail

fallback:
  pressure-aware head/tail
```

### Task 4: Add Summarizer Interface and Router

**Files:**
- Create: `src/main/java/com/tcode/context/ToolResultSummarizer.java`
- Create: `src/main/java/com/tcode/context/ToolResultSummarizers.java`
- Create: `src/main/java/com/tcode/context/DefaultHeadTailToolResultSummarizer.java`
- Modify: `src/main/java/com/tcode/context/ContextManager.java`
- Test: `src/test/java/com/tcode/context/ToolResultSummarizersTest.java`

- [x] **Step 1: Write failing router test**

```java
@Test
void fallsBackToHeadTailSummarizerForUnknownTools() {
    String result = "x".repeat(5_000);

    String summarized = ToolResultSummarizers.defaults()
            .summarize("unknown_tool", result, ToolSummaryPolicy.forLevel(ContextPressureLevel.NORMAL));

    assertTrue(summarized.contains("Tool: unknown_tool"));
    assertTrue(summarized.contains("--- head ---"));
    assertTrue(summarized.contains("--- tail ---"));
}
```

- [x] **Step 2: Implement interface**

```java
package com.tcode.context;

public interface ToolResultSummarizer {
    boolean supports(String toolName);

    String summarize(String toolName, String result, ToolSummaryPolicy policy);
}
```

- [x] **Step 3: Implement default summarizer**

Move current head/tail logic into `DefaultHeadTailToolResultSummarizer`.

- [x] **Step 4: Implement router**

```java
public final class ToolResultSummarizers {
    private final List<ToolResultSummarizer> summarizers;

    public static ToolResultSummarizers defaults() {
        return new ToolResultSummarizers(List.of(
                new ExecuteCommandToolResultSummarizer(),
                new GrepCodeToolResultSummarizer(),
                new ReadFileToolResultSummarizer(),
                new DefaultHeadTailToolResultSummarizer()
        ));
    }
}
```

- [x] **Step 5: Wire into `ContextManager`**

Add field:

```java
private final ToolResultSummarizers toolResultSummarizers = ToolResultSummarizers.defaults();
```

Use:

```java
String stored = toolResultSummarizers.summarize(
        toolName, result, ToolSummaryPolicy.forLevel(pressureLevel()));
```

- [x] **Step 6: Run tests**

```bash
mvn test -Dtest=ContextManagerTest,ToolResultSummarizersTest -DskipTests=false
```

Expected: pass.

### Task 5: Add `execute_command` Summarizer

**Files:**
- Create: `src/main/java/com/tcode/context/ExecuteCommandToolResultSummarizer.java`
- Test: `src/test/java/com/tcode/context/ExecuteCommandToolResultSummarizerTest.java`

- [x] **Step 1: Write failing test**

```java
@Test
void keepsFailuresAndTailForCommandOutput() {
    String output = """
            [exit code: 1]
            lots
            lots
            FAILURE: ContextManagerTest.shouldCompact
            expected true but was false
            """ + "\nline".repeat(2_000);

    String summarized = new ExecuteCommandToolResultSummarizer()
            .summarize("execute_command", output, ToolSummaryPolicy.forLevel(ContextPressureLevel.COMPACT));

    assertTrue(summarized.contains("[execute_command summarized]"));
    assertTrue(summarized.contains("exit code: 1"));
    assertTrue(summarized.contains("FAILURE"));
    assertTrue(summarized.contains("--- tail ---"));
}
```

- [x] **Step 2: Implement deterministic summary**

Keep:
- first line containing `exit code`
- lines containing `FAIL`, `ERROR`, `Exception`, `Caused by`, `expected`, `actual`
- last `edgeChars` characters

- [x] **Step 3: Run test**

```bash
mvn test -Dtest=ExecuteCommandToolResultSummarizerTest -DskipTests=false
```

Expected: pass.

### Task 6: Add `grep_code` and `read_file` Summarizers

**Files:**
- Create: `src/main/java/com/tcode/context/GrepCodeToolResultSummarizer.java`
- Create: `src/main/java/com/tcode/context/ReadFileToolResultSummarizer.java`
- Test: `src/test/java/com/tcode/context/GrepCodeToolResultSummarizerTest.java`
- Test: `src/test/java/com/tcode/context/ReadFileToolResultSummarizerTest.java`

- [x] **Step 1: Test grep summary preserves path and line hits**

Expected summary:

```text
[grep_code summarized]
Matches shown:
src/main/java/com/tcode/agent/Agent.java:72 ...
```

- [x] **Step 2: Test read_file summary preserves file and structural lines**

Expected summary keeps lines matching:

```text
package
import
class
interface
enum
record
public/private/protected method-like declarations
```

- [x] **Step 3: Implement both summarizers**

Use deterministic line filters only. Do not call LLM.

- [x] **Step 4: Run tests**

```bash
mvn test -Dtest=GrepCodeToolResultSummarizerTest,ReadFileToolResultSummarizerTest -DskipTests=false
```

Expected: pass.

---

## Phase 4: Manual `/compact [focus]`

### Task 7: Parse `/compact`

**Files:**
- Modify: `src/main/java/com/tcode/cli/CliCommandParser.java`
- Modify: `src/main/java/com/tcode/cli/CliPresentation.java`
- Test: `src/test/java/com/tcode/cli/CliCommandParserTest.java`
- Test: `src/test/java/com/tcode/cli/MainInputNormalizationTest.java`

- [x] **Step 1: Write failing parser tests**

```java
@Test
void parsesCompactCommandWithOptionalFocus() {
    assertEquals(CliCommandParser.CommandType.CONTEXT_COMPACT,
            CliCommandParser.parse("/compact").type());
    assertEquals("保留失败测试和已修改文件",
            CliCommandParser.parse("/compact 保留失败测试和已修改文件").payload());
}
```

- [x] **Step 2: Add command type and parse branch**

Add:

```java
CONTEXT_COMPACT
```

Parse:

```java
if (trimmed.equalsIgnoreCase("/compact")) {
    return new ParsedCommand(CommandType.CONTEXT_COMPACT, "");
}
if (trimmed.regionMatches(true, 0, "/compact ", 0, 9)) {
    return new ParsedCommand(CommandType.CONTEXT_COMPACT, trimmed.substring(9).trim());
}
```

- [x] **Step 3: Add slash help**

Add `/compact [focus]` to `CliPresentation`.

- [x] **Step 4: Run parser tests**

```bash
mvn test -Dtest=CliCommandParserTest,MainInputNormalizationTest -DskipTests=false
```

Expected: pass.

### Task 8: Add Force Compaction API

**Files:**
- Modify: `src/main/java/com/tcode/context/ConversationHistoryCompactor.java`
- Modify: `src/main/java/com/tcode/context/ContextManager.java`
- Modify: `src/main/java/com/tcode/agent/Agent.java`
- Test: `src/test/java/com/tcode/context/ConversationHistoryCompactorTest.java`

- [x] **Step 1: Add tests for forced compaction**

Manual compaction should not require 85% usage, but should still skip if there are not enough user turns to summarize.

- [x] **Step 2: Add focus to compactor prompt**

Add overload:

```java
public boolean compactNow(List<LlmClient.Message> history, String focus)
```

If focus is non-blank, include:

```text
User focus for this compaction:
<focus>

Prioritize preserving information related to this focus.
```

- [x] **Step 3: Expose from `ContextManager`**

```java
public boolean compactNow(PrintStream out, String focus) {
    boolean compacted = compactor.compactNow(messages, focus);
    if (compacted) {
        historyCompactions++;
    }
    return compacted;
}
```

- [x] **Step 4: Expose from `Agent`**

```java
public boolean compactContext(String focus) {
    refreshSystemPrompt();
    boolean compacted = contextManager.compactNow(System.out, focus);
    refreshSystemPrompt();
    return compacted;
}
```

- [x] **Step 5: Run tests**

```bash
mvn test -Dtest=ConversationHistoryCompactorTest,ContextManagerTest,AgentContextStatusTest -DskipTests=false
```

Expected: pass.

### Task 9: Dispatch `/compact`

**Files:**
- Modify: `src/main/java/com/tcode/cli/CliConversationCommandDispatcher.java`
- Test: `src/test/java/com/tcode/cli/CliConversationCommandDispatcherTest.java`

- [x] **Step 1: Add failing dispatch test**

Test that `CONTEXT_COMPACT` calls `Agent.compactContext(payload)`.

- [x] **Step 2: Implement dispatch branch**

```java
case CONTEXT_COMPACT -> {
    boolean compacted = context.reactAgent().compactContext(command.payload());
    context.ui().println(compacted
            ? "上下文已压缩。\n"
            : "当前上下文较短，暂不需要压缩。\n");
    yield true;
}
```

- [x] **Step 3: Run tests**

```bash
mvn test -Dtest=CliConversationCommandDispatcherTest,CliCommandParserTest -DskipTests=false
```

Expected: pass.

---

## Phase 5: Lossless Context Event Store

This phase preserves raw context without feeding it back into the LLM automatically.

The active context and raw event log have different jobs:

```text
activeMessages:
  - sent to the LLM
  - can be summarized, compacted, and shortened

events.jsonl:
  - local lossless record
  - append-only
  - searched by metadata or keyword
  - not included in LLM calls unless a later explicit inject command is used
```

The first recall version is deliberately read-only:

```text
/context recall <keyword>  -> show matching event ids and snippets
/context show <event_id>   -> show full raw event content
```

Do not implement automatic injection in this phase. Automatic recall can bloat context and reintroduce irrelevant history. If needed later, add explicit `/context inject <event_id>` as a separate feature.

### Task 10: Add Event Model and Store

**Files:**
- Create: `src/main/java/com/tcode/context/ContextEvent.java`
- Create: `src/main/java/com/tcode/context/ContextEventStore.java`
- Create: `src/main/java/com/tcode/context/JsonlContextEventStore.java`
- Test: `src/test/java/com/tcode/context/JsonlContextEventStoreTest.java`

- [x] **Step 1: Write failing persistence test**

```java
@Test
void appendsAndSearchesEvents() {
    JsonlContextEventStore store = new JsonlContextEventStore(tempDir.resolve("events.jsonl"));
    store.append(ContextEvent.tool("turn-1", "read_file", "full content", Map.of("file", "Agent.java")));

    List<ContextEvent> results = store.search("full", 10);

    assertEquals(1, results.size());
    assertEquals("read_file", results.get(0).toolName());
}
```

- [x] **Step 2: Implement JSONL store**

Fields:

```java
id
turnId
role
toolName
content
metadata
createdAt
```

- [x] **Step 3: Run tests**

```bash
mvn test -Dtest=JsonlContextEventStoreTest -DskipTests=false
```

Expected: pass.

### Task 11: Record Raw Events from `ContextManager`

**Files:**
- Modify: `src/main/java/com/tcode/context/ContextManager.java`
- Test: `src/test/java/com/tcode/context/ContextManagerTest.java`

- [x] **Step 1: Add test**

When adding a long tool result, event store should receive full raw content while active messages receive summary.

- [x] **Step 2: Add optional store dependency**

Constructor overload:

```java
public ContextManager(LlmClient llmClient, ContextProfile contextProfile, ContextEventStore eventStore)
```

Use a no-op store when null.

- [x] **Step 3: Record events**

Record:
- user messages
- assistant messages
- tool raw results
- compaction summaries

- [x] **Step 4: Run tests**

```bash
mvn test -Dtest=ContextManagerTest,JsonlContextEventStoreTest -DskipTests=false
```

Expected: pass.

### Task 12: Add Recall Commands

**Files:**
- Modify: `src/main/java/com/tcode/cli/CliCommandParser.java`
- Modify: `src/main/java/com/tcode/cli/CliConversationCommandDispatcher.java`
- Modify: `src/main/java/com/tcode/cli/CliPresentation.java`
- Test: `src/test/java/com/tcode/cli/CliCommandParserTest.java`

- [x] **Step 1: Parse commands**

```text
/context events
/context recall <keyword>
/context show <event_id>
```

- [x] **Step 2: Implement read-only dispatch**

Search and display event summaries. Do not inject recalled events into active messages automatically.

- [x] **Step 3: Run tests**

```bash
mvn test -Dtest=CliCommandParserTest,CliConversationCommandDispatcherTest -DskipTests=false
```

Expected: pass.

---

## Phase 6: Explicit Context Injection

This phase is optional and should start only after read-only recall is useful in real tasks.

### Task 13: Add `/context inject <event_id>`

**Files:**
- Modify: `src/main/java/com/tcode/cli/CliCommandParser.java`
- Modify: `src/main/java/com/tcode/cli/CliConversationCommandDispatcher.java`
- Modify: `src/main/java/com/tcode/context/ContextManager.java`
- Test: `src/test/java/com/tcode/cli/CliCommandParserTest.java`
- Test: `src/test/java/com/tcode/context/ContextManagerTest.java`

- [x] **Step 1: Add parser tests**

```java
@Test
void parsesContextInjectCommand() {
    CliCommandParser.ParsedCommand command = CliCommandParser.parse("/context inject evt_123");

    assertEquals(CliCommandParser.CommandType.CONTEXT_INJECT, command.type());
    assertEquals("evt_123", command.payload());
}
```

- [x] **Step 2: Implement explicit injection**

Inject a compact user-visible marker into active context:

```text
[Recalled context event: evt_123]
<event content, bounded by current ToolSummaryPolicy>
```

The injection must be explicit and bounded. Do not inject search results automatically.

- [x] **Step 3: Run tests**

```bash
mvn test -Dtest=CliCommandParserTest,ContextManagerTest,CliConversationCommandDispatcherTest -DskipTests=false
```

Expected: pass.

---

## Phase 7: Async Pre-Compaction

### Task 14: Add Pending Compaction Snapshot

**Files:**
- Create: `src/main/java/com/tcode/context/PendingCompaction.java`
- Modify: `src/main/java/com/tcode/context/ConversationHistoryCompactor.java`
- Test: `src/test/java/com/tcode/context/ConversationHistoryCompactorTest.java`

- [ ] **Step 1: Add tests**

Test that pending summary records:

```java
baseMessageCount
splitIndex
summary
createdAt
```

- [ ] **Step 2: Implement snapshot summarization**

Add:

```java
public PendingCompaction summarizeSnapshot(List<LlmClient.Message> snapshot, String focus)
```

This method must not mutate `snapshot`.

- [ ] **Step 3: Run tests**

```bash
mvn test -Dtest=ConversationHistoryCompactorTest -DskipTests=false
```

Expected: pass.

### Task 15: Background Pre-Compaction

**Files:**
- Modify: `src/main/java/com/tcode/context/ContextManager.java`
- Test: `src/test/java/com/tcode/context/ContextManagerTest.java`

- [ ] **Step 1: Add tests**

When pressure reaches `CONSERVE`, `ContextManager` can start a background summary without mutating active messages.

- [ ] **Step 2: Implement single-thread executor**

Rules:
- Start at most one pre-compaction job at a time.
- Job uses `messages()` copy.
- Job only writes `pendingCompaction`.
- `close()` cancels executor if `ContextManager` becomes closeable.

- [ ] **Step 3: Apply pending summary at COMPACT**

If pending summary matches current message shape, apply it. Otherwise fall back to synchronous compaction.

- [ ] **Step 4: Run tests**

```bash
mvn test -Dtest=ContextManagerTest,ConversationHistoryCompactorTest -DskipTests=false
```

Expected: pass.

---

## Phase 8: Final Verification

### Task 16: Regression and Manual Acceptance

**Files:** no source changes.

- [ ] **Step 1: Run focused tests**

```bash
mvn test -Dtest=ContextManagerTest,ConversationHistoryCompactorTest,AgentContextStatusTest,CliCommandParserTest,CliConversationCommandDispatcherTest -DskipTests=false
```

Expected: pass.

- [ ] **Step 2: Run quick regression**

```bash
mvn test -Pquick
```

Expected: pass.

- [ ] **Step 3: Run clean package**

```bash
mvn clean package
```

Expected: build success.

- [ ] **Step 4: Manual CLI acceptance**

Use Java 17:

```powershell
$env:GLM_API_KEY='acceptance-dummy-key'
$env:TCODE_RENDERER='plain'
"/context", "/compact 保留失败测试和已修改文件", "/context", "/exit" |
  D:\Develop\jdk17\bin\java.exe -jar target\t-code-1.0-SNAPSHOT.jar
```

Expected:
- `/context` shows pressure level.
- `/compact` returns a clear success or "not enough history" message.
- CLI exits with code `0`.

---

## Acceptance Criteria

- `/context` displays pressure level and summary policy.
- Tool result summaries tighten as pressure rises.
- `execute_command`, `grep_code`, and `read_file` use deterministic structured summaries.
- `/compact [focus]` is supported and tested.
- Raw context events are persisted in JSONL and searchable without reintroducing RAG.
- Recall is read-only by default; explicit injection is a separate bounded command.
- Async pre-compaction never mutates active messages from a background thread.
- `mvn test -Pquick` and `mvn clean package` pass.

## Risks

- Structured summaries can accidentally omit important data. Keep default head/tail fallback and make each summarizer deterministic and heavily tested.
- Async compaction can introduce race conditions. Only apply pending summaries on the main context path after validating message counts and split index.
- Event logs may grow large. Add retention later; do not block MVP on retention policies.

## Non-Goals

- Do not change long-term memory behavior.
- Do not add vector search or RAG.
- Do not auto-inject recalled raw events into active context.
- Do not make tool execution itself depend on context pressure in this iteration.
