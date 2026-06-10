# Plan Execute Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Strengthen the existing Plan-and-Execute mode so generated plans are validated, estimated before execution, protected from unsafe parallelism, classified on failure, and traced for future resume/evaluation work.

**Architecture:** Keep the current `Planner -> ExecutionPlan -> PlanExecuteAgent` flow. Add small focused classes under `com.tcode.plan` for validation, estimation, resource locking, failure classification, and tracing; wire them into `Planner` and `PlanExecuteAgent` without changing CLI behavior first. The first milestone is hardening and observability, not full checkpoint/resume.

**Tech Stack:** Java 17, Maven, JUnit 5, existing `ToolRegistry`, existing `ExecutionPlan` / `Task` model.

---

## Current Problems To Address

1. `Planner.parsePlan()` only validates JSON parseability and DAG acyclicity; it does not reject structurally weak plans.
2. `Task` has only `id`, `description`, `type`, and dependencies; it lacks expected output and resource hints.
3. Users see a plan summary but not a cost/time/risk estimate before approving execution.
4. Parallel task batches are based only on DAG readiness; they do not account for shared mutable resources such as files, shell, browser, or MCP state.
5. Failure handling uses `plan.getProgress() < 0.5` as the main replan trigger; it does not distinguish transient tool errors, validation failures, policy denials, or plan defects.
6. There is no durable trace of planner output, task state transitions, tool calls, or replan decisions.

## Proposed File Structure

- Create `src/main/java/com/tcode/plan/PlanValidationIssue.java`
  - Immutable issue object with severity, code, task id, and message.
- Create `src/main/java/com/tcode/plan/PlanValidationResult.java`
  - Holds validation issues and helper methods such as `hasErrors()`.
- Create `src/main/java/com/tcode/plan/PlanValidator.java`
  - Validates plan shape, task descriptions, dependencies, verification coverage, and risky parallel plans.
- Create `src/main/java/com/tcode/plan/PlanEstimate.java`
  - Immutable estimate object with task count, DAG batch count, effort score, estimated minutes, risk level, and review recommendation.
- Create `src/main/java/com/tcode/plan/PlanEstimator.java`
  - Computes conservative estimates from task types, dependency shape, resource locks, and verification coverage.
- Create `src/main/java/com/tcode/plan/PlanResourceLock.java`
  - Represents logical execution locks such as `tool:shell`, `tool:browser`, and `file:path`.
- Create `src/main/java/com/tcode/plan/PlanFailureClassifier.java`
  - Classifies exceptions/tool result text into retry, replan, stop, or verification-fix actions.
- Create `src/main/java/com/tcode/plan/PlanRunTrace.java`
  - In-memory trace events for plan creation, validation, task start/end, tool calls, failures, and replans.
- Modify `src/main/java/com/tcode/plan/Task.java`
  - Add optional metadata fields with conservative defaults.
- Modify `src/main/java/com/tcode/plan/Planner.java`
  - Parse optional metadata, validate generated plans, and surface validation errors.
- Modify `src/main/java/com/tcode/agent/PlanExecuteAgent.java`
  - Display plan estimates during review, use resource-aware batching, failure classification, and trace recording.
- Modify `src/main/resources/prompts/modes/planner.md`
  - Ask the planner for optional metadata while preserving backwards compatibility.
- Add tests:
  - `src/test/java/com/tcode/plan/PlanValidatorTest.java`
  - `src/test/java/com/tcode/plan/PlanEstimatorTest.java`
  - `src/test/java/com/tcode/plan/PlanFailureClassifierTest.java`
  - Extend `src/test/java/com/tcode/cli/CliPlanReviewHandlerTest.java`
  - Extend `src/test/java/com/tcode/plan/PlannerTest.java`
  - Extend `src/test/java/com/tcode/agent/PlanExecuteAgentTest.java`

---

### Task 1: Add Plan Validation

**Files:**
- Create: `src/main/java/com/tcode/plan/PlanValidationIssue.java`
- Create: `src/main/java/com/tcode/plan/PlanValidationResult.java`
- Create: `src/main/java/com/tcode/plan/PlanValidator.java`
- Modify: `src/main/java/com/tcode/plan/Planner.java`
- Test: `src/test/java/com/tcode/plan/PlanValidatorTest.java`
- Test: `src/test/java/com/tcode/plan/PlannerTest.java`

- [ ] **Step 1: Write failing tests for validator**

Create `src/test/java/com/tcode/plan/PlanValidatorTest.java`:

```java
package com.tcode.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanValidatorTest {

    @Test
    void rejectsEmptyTaskDescription() {
        ExecutionPlan plan = new ExecutionPlan("plan_1", "demo");
        plan.addTask(new Task("task_1", "   ", Task.TaskType.ANALYSIS));
        plan.computeExecutionOrder();

        PlanValidationResult result = new PlanValidator().validate(plan);

        assertTrue(result.hasErrors());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("EMPTY_TASK_DESCRIPTION")));
    }

    @Test
    void warnsWhenWriteTaskHasNoVerificationDependent() {
        ExecutionPlan plan = new ExecutionPlan("plan_2", "change code");
        plan.addTask(new Task("task_1", "modify source file", Task.TaskType.FILE_WRITE));
        plan.computeExecutionOrder();

        PlanValidationResult result = new PlanValidator().validate(plan);

        assertFalse(result.hasErrors());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("MISSING_VERIFICATION")));
    }

    @Test
    void acceptsWriteTaskWithVerificationDependent() {
        ExecutionPlan plan = new ExecutionPlan("plan_3", "change code");
        plan.addTask(new Task("task_1", "modify source file", Task.TaskType.FILE_WRITE));
        plan.addTask(new Task("task_2", "run focused test", Task.TaskType.VERIFICATION, List.of("task_1")));
        plan.computeExecutionOrder();

        PlanValidationResult result = new PlanValidator().validate(plan);

        assertFalse(result.hasErrors());
        assertTrue(result.issues().stream().noneMatch(issue -> issue.code().equals("MISSING_VERIFICATION")));
    }
}
```

- [ ] **Step 2: Run validator test and verify it fails**

Run:

```bash
mvn test -Dtest=PlanValidatorTest -DskipTests=false
```

Expected: compilation failure because `PlanValidator` classes do not exist.

- [ ] **Step 3: Implement validation types**

Create `PlanValidationIssue.java`:

```java
package com.tcode.plan;

public record PlanValidationIssue(Severity severity, String code, String taskId, String message) {
    public enum Severity {
        ERROR,
        WARNING
    }
}
```

Create `PlanValidationResult.java`:

```java
package com.tcode.plan;

import java.util.ArrayList;
import java.util.List;

public record PlanValidationResult(List<PlanValidationIssue> issues) {
    public PlanValidationResult {
        issues = List.copyOf(issues == null ? List.of() : issues);
    }

    public static PlanValidationResult ok() {
        return new PlanValidationResult(List.of());
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> issue.severity() == PlanValidationIssue.Severity.ERROR);
    }

    public String formatErrors() {
        List<String> lines = new ArrayList<>();
        for (PlanValidationIssue issue : issues) {
            if (issue.severity() == PlanValidationIssue.Severity.ERROR) {
                lines.add(issue.code() + (issue.taskId() == null ? "" : " [" + issue.taskId() + "]") + ": " + issue.message());
            }
        }
        return String.join("\n", lines);
    }
}
```

Create `PlanValidator.java`:

```java
package com.tcode.plan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PlanValidator {

    public PlanValidationResult validate(ExecutionPlan plan) {
        if (plan == null) {
            return new PlanValidationResult(List.of(error("NULL_PLAN", null, "plan is null")));
        }

        List<PlanValidationIssue> issues = new ArrayList<>();
        if (plan.getAllTasks().isEmpty()) {
            issues.add(error("EMPTY_PLAN", null, "plan has no tasks"));
        }

        for (Task task : plan.getAllTasks()) {
            if (task.getDescription() == null || task.getDescription().trim().isEmpty()) {
                issues.add(error("EMPTY_TASK_DESCRIPTION", task.getId(), "task description must not be blank"));
            }
            for (String depId : task.getDependencies()) {
                if (plan.getTask(depId) == null) {
                    issues.add(error("UNKNOWN_DEPENDENCY", task.getId(), "dependency does not exist: " + depId));
                }
            }
            if (requiresVerification(task) && !hasVerificationDependent(plan, task)) {
                issues.add(warning("MISSING_VERIFICATION", task.getId(),
                        "write or command task should be followed by a verification task"));
            }
        }

        if (!plan.computeExecutionOrder()) {
            issues.add(error("CYCLIC_DEPENDENCY", null, "plan contains cyclic dependencies"));
        }

        return new PlanValidationResult(issues);
    }

    private boolean requiresVerification(Task task) {
        return task.getType() == Task.TaskType.FILE_WRITE || task.getType() == Task.TaskType.COMMAND;
    }

    private boolean hasVerificationDependent(ExecutionPlan plan, Task task) {
        Set<String> visited = new HashSet<>();
        return hasVerificationDependent(plan, task.getId(), visited);
    }

    private boolean hasVerificationDependent(ExecutionPlan plan, String taskId, Set<String> visited) {
        if (!visited.add(taskId)) {
            return false;
        }
        Task task = plan.getTask(taskId);
        if (task == null) {
            return false;
        }
        for (String dependentId : task.getDependents()) {
            Task dependent = plan.getTask(dependentId);
            if (dependent == null) {
                continue;
            }
            if (dependent.getType() == Task.TaskType.VERIFICATION) {
                return true;
            }
            if (hasVerificationDependent(plan, dependentId, visited)) {
                return true;
            }
        }
        return false;
    }

    private static PlanValidationIssue error(String code, String taskId, String message) {
        return new PlanValidationIssue(PlanValidationIssue.Severity.ERROR, code, taskId, message);
    }

    private static PlanValidationIssue warning(String code, String taskId, String message) {
        return new PlanValidationIssue(PlanValidationIssue.Severity.WARNING, code, taskId, message);
    }
}
```

- [ ] **Step 4: Wire validator into `Planner.parsePlan()`**

After `plan.computeExecutionOrder()` succeeds in `Planner.parsePlan()`, add:

```java
PlanValidationResult validation = new PlanValidator().validate(plan);
if (validation.hasErrors()) {
    throw new IOException("规划校验失败:\n" + validation.formatErrors());
}
```

Do not fail on warnings yet; warnings can be shown in a later CLI polish task.

- [ ] **Step 5: Run tests**

Run:

```bash
mvn test -Dtest=PlanValidatorTest,PlannerTest,ExecutionPlanTest -DskipTests=false
```

Expected: all pass.

---

### Task 2: Extend Task Metadata Without Breaking Existing Plans

**Files:**
- Modify: `src/main/java/com/tcode/plan/Task.java`
- Modify: `src/main/java/com/tcode/plan/Planner.java`
- Modify: `src/main/resources/prompts/modes/planner.md`
- Test: `src/test/java/com/tcode/plan/PlannerTest.java`
- Test: `src/test/java/com/tcode/plan/ExecutionPlanTest.java`

- [ ] **Step 1: Add failing parser test for optional metadata**

Add to `PlannerTest`:

```java
@Test
void parsesOptionalTaskMetadata() throws Exception {
    Planner planner = new Planner(new StubGLMClient("""
            {
              "summary": "metadata plan",
              "tasks": [
                {
                  "id": "read",
                  "description": "read pom",
                  "type": "FILE_READ",
                  "expected_output": "pom contents summarized",
                  "resource_locks": ["file:pom.xml"],
                  "dependencies": []
                }
              ]
            }
            """));

    ExecutionPlan plan = planner.createPlan("先读取 pom.xml 然后总结依赖");
    Task task = plan.getTask("task_1");

    assertEquals("pom contents summarized", task.getExpectedOutput());
    assertEquals(List.of("file:pom.xml"), task.getResourceLocks());
}
```

- [ ] **Step 2: Run parser test and verify it fails**

Run:

```bash
mvn test -Dtest=PlannerTest#parsesOptionalTaskMetadata -DskipTests=false
```

Expected: compilation failure because metadata getters do not exist.

- [ ] **Step 3: Add metadata fields to `Task`**

Add fields:

```java
private String expectedOutput;
private final List<String> resourceLocks;
```

Initialize them in the main constructor:

```java
this.resourceLocks = new ArrayList<>();
```

Add getters and setters:

```java
public String getExpectedOutput() { return expectedOutput; }
public List<String> getResourceLocks() { return new ArrayList<>(resourceLocks); }

public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }

public void setResourceLocks(List<String> locks) {
    resourceLocks.clear();
    if (locks != null) {
        locks.stream()
                .filter(lock -> lock != null && !lock.isBlank())
                .map(String::trim)
                .distinct()
                .forEach(resourceLocks::add);
    }
}
```

- [ ] **Step 4: Parse optional metadata in `Planner.parsePlan()`**

After creating each `Task`, parse optional fields:

```java
Task task = new Task(newId, description, type);
task.setExpectedOutput(taskNode.path("expected_output").asText(null));
task.setResourceLocks(readStringArray(taskNode.path("resource_locks")));
plan.addTask(task);
```

Add helper method to `Planner`:

```java
private List<String> readStringArray(JsonNode node) {
    if (node == null || !node.isArray()) {
        return List.of();
    }
    List<String> values = new ArrayList<>();
    for (JsonNode value : node) {
        String text = value.asText("");
        if (!text.isBlank()) {
            values.add(text.trim());
        }
    }
    return values;
}
```

- [ ] **Step 5: Update planner prompt**

In `src/main/resources/prompts/modes/planner.md`, extend the JSON example task with optional fields:

```json
"expected_output": "本任务完成后应产出的可核验结果",
"resource_locks": ["file:pom.xml"]
```

Add rule:

```text
9. 如能判断任务的输出或资源锁，请填写 expected_output / resource_locks；不确定时可省略。
```

- [ ] **Step 6: Run tests**

Run:

```bash
mvn test -Dtest=PlannerTest,ExecutionPlanTest -DskipTests=false
```

Expected: all pass.

---

### Task 3: Add Plan Estimation

**Files:**
- Create: `src/main/java/com/tcode/plan/PlanEstimate.java`
- Create: `src/main/java/com/tcode/plan/PlanEstimator.java`
- Modify: `src/main/java/com/tcode/plan/ExecutionPlan.java`
- Modify: `src/main/java/com/tcode/cli/CliPlanReviewHandler.java`
- Modify: `src/main/java/com/tcode/agent/PlanExecuteAgent.java`
- Test: `src/test/java/com/tcode/plan/PlanEstimatorTest.java`
- Test: `src/test/java/com/tcode/cli/CliPlanReviewHandlerTest.java`

- [ ] **Step 1: Write failing estimator tests**

Create `src/test/java/com/tcode/plan/PlanEstimatorTest.java`:

```java
package com.tcode.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanEstimatorTest {

    @Test
    void estimatesSmallReadOnlyPlanAsLowRisk() {
        ExecutionPlan plan = new ExecutionPlan("plan_1", "inspect project");
        plan.addTask(new Task("task_1", "read pom", Task.TaskType.FILE_READ));
        plan.addTask(new Task("task_2", "summarize", Task.TaskType.ANALYSIS, List.of("task_1")));
        plan.computeExecutionOrder();

        PlanEstimate estimate = new PlanEstimator().estimate(plan);

        assertEquals(2, estimate.taskCount());
        assertEquals(2, estimate.batchCount());
        assertEquals(PlanEstimate.RiskLevel.LOW, estimate.riskLevel());
        assertTrue(estimate.estimatedMinutes() >= 1);
    }

    @Test
    void estimatesWriteAndCommandPlanAsHighRisk() {
        ExecutionPlan plan = new ExecutionPlan("plan_2", "change code");
        plan.addTask(new Task("task_1", "modify source", Task.TaskType.FILE_WRITE));
        plan.addTask(new Task("task_2", "run tests", Task.TaskType.COMMAND, List.of("task_1")));
        plan.addTask(new Task("task_3", "verify result", Task.TaskType.VERIFICATION, List.of("task_2")));
        plan.computeExecutionOrder();

        PlanEstimate estimate = new PlanEstimator().estimate(plan);

        assertEquals(PlanEstimate.RiskLevel.HIGH, estimate.riskLevel());
        assertTrue(estimate.effortScore() >= 8);
        assertTrue(estimate.reviewRecommendation().contains("expand"));
    }

    @Test
    void estimateSummaryIsHumanReadable() {
        ExecutionPlan plan = new ExecutionPlan("plan_3", "demo");
        plan.addTask(new Task("task_1", "read files", Task.TaskType.FILE_READ));
        plan.computeExecutionOrder();

        String summary = new PlanEstimator().estimate(plan).formatForReview();

        assertTrue(summary.contains("Estimate"));
        assertTrue(summary.contains("tasks=1"));
        assertTrue(summary.contains("risk=LOW"));
    }
}
```

- [ ] **Step 2: Run estimator test and verify it fails**

Run:

```bash
mvn test -Dtest=PlanEstimatorTest -DskipTests=false
```

Expected: compilation failure because `PlanEstimate` and `PlanEstimator` do not exist.

- [ ] **Step 3: Implement `PlanEstimate`**

Create `src/main/java/com/tcode/plan/PlanEstimate.java`:

```java
package com.tcode.plan;

public record PlanEstimate(int taskCount,
                           int batchCount,
                           int effortScore,
                           int estimatedMinutes,
                           RiskLevel riskLevel,
                           String reviewRecommendation) {
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    public String formatForReview() {
        return "Estimate: tasks=" + taskCount
                + ", batches=" + batchCount
                + ", effort=" + effortScore
                + ", minutes~" + estimatedMinutes
                + ", risk=" + riskLevel
                + ", review=" + reviewRecommendation;
    }
}
```

- [ ] **Step 4: Implement `PlanEstimator`**

Create `src/main/java/com/tcode/plan/PlanEstimator.java`:

```java
package com.tcode.plan;

public final class PlanEstimator {

    public PlanEstimate estimate(ExecutionPlan plan) {
        if (plan == null || plan.getAllTasks().isEmpty()) {
            return new PlanEstimate(0, 0, 0, 0, PlanEstimate.RiskLevel.LOW, "none");
        }

        int effort = 0;
        int risk = 0;
        for (Task task : plan.getAllTasks()) {
            effort += effortWeight(task);
            risk += riskWeight(task);
            if (!task.getResourceLocks().isEmpty()) {
                risk += 1;
            }
        }

        int taskCount = plan.getAllTasks().size();
        int batchCount = plan.getExecutionBatches().size();
        int estimatedMinutes = Math.max(1, Math.min(90, effort * 2));
        PlanEstimate.RiskLevel riskLevel = riskLevel(risk, effort, taskCount);
        String recommendation = switch (riskLevel) {
            case LOW -> "normal";
            case MEDIUM -> "review summary";
            case HIGH -> "expand plan before execution";
        };

        return new PlanEstimate(taskCount, batchCount, effort, estimatedMinutes, riskLevel, recommendation);
    }

    private int effortWeight(Task task) {
        return switch (task.getType()) {
            case PLANNING -> 1;
            case FILE_READ -> 1;
            case ANALYSIS -> 2;
            case FILE_WRITE -> 4;
            case COMMAND -> 3;
            case VERIFICATION -> 2;
        };
    }

    private int riskWeight(Task task) {
        return switch (task.getType()) {
            case FILE_WRITE -> 3;
            case COMMAND -> 3;
            case VERIFICATION -> 1;
            default -> 0;
        };
    }

    private PlanEstimate.RiskLevel riskLevel(int risk, int effort, int taskCount) {
        if (risk >= 5 || effort >= 10 || taskCount >= 6) {
            return PlanEstimate.RiskLevel.HIGH;
        }
        if (risk >= 2 || effort >= 5 || taskCount >= 3) {
            return PlanEstimate.RiskLevel.MEDIUM;
        }
        return PlanEstimate.RiskLevel.LOW;
    }
}
```

- [ ] **Step 5: Store estimate on `ExecutionPlan`**

Add field and accessors to `ExecutionPlan`:

```java
private PlanEstimate estimate;

public PlanEstimate getEstimate() { return estimate; }
public void setEstimate(PlanEstimate estimate) { this.estimate = estimate; }
```

At the end of `Planner.parsePlan()` and `createMinimalPlan()`, set:

```java
plan.setEstimate(new PlanEstimator().estimate(plan));
```

- [ ] **Step 6: Show estimate in plan review**

In `CliPlanReviewHandler.create(...)`, after `out.println(plan.summarize());`, add:

```java
if (plan.getEstimate() != null) {
    out.println(plan.getEstimate().formatForReview());
}
```

If a plan is built in tests without `Planner`, compute lazily:

```java
PlanEstimate estimate = plan.getEstimate() == null ? new PlanEstimator().estimate(plan) : plan.getEstimate();
out.println(estimate.formatForReview());
```

- [ ] **Step 7: Run tests**

Run:

```bash
mvn test -Dtest=PlanEstimatorTest,CliPlanReviewHandlerTest,PlannerTest,ExecutionPlanTest,PlanExecuteAgentTest -DskipTests=false
```

Expected: all pass.

---

### Task 4: Add Resource-Aware Parallel Batching

**Files:**
- Create: `src/main/java/com/tcode/plan/PlanResourceLock.java`
- Modify: `src/main/java/com/tcode/agent/PlanExecuteAgent.java`
- Test: `src/test/java/com/tcode/agent/PlanExecuteAgentTest.java`

- [ ] **Step 1: Add tests for serializing conflicting tasks**

Add a package-visible method in `PlanExecuteAgent` during implementation:

```java
List<List<Task>> splitIntoResourceSafeBatches(List<Task> executableTasks)
```

Then add test in `PlanExecuteAgentTest`:

```java
@Test
void splitsParallelTasksWhenResourceLocksConflict() {
    StubGLMClient llmClient = new StubGLMClient(List.of());
    PlanExecuteAgent agent = new PlanExecuteAgent(
            llmClient,
            new ToolRegistry(),
            new StubPlanner(llmClient),
            null,
            (goal, plan) -> PlanExecuteAgent.PlanReviewDecision.execute()
    );

    Task first = new Task("task_1", "write file", Task.TaskType.FILE_WRITE);
    first.setResourceLocks(List.of("file:src/App.java"));
    Task second = new Task("task_2", "write same file", Task.TaskType.FILE_WRITE);
    second.setResourceLocks(List.of("file:src/App.java"));
    Task third = new Task("task_3", "read pom", Task.TaskType.FILE_READ);

    List<List<Task>> batches = agent.splitIntoResourceSafeBatches(List.of(first, second, third));

    assertEquals(List.of(first, third), batches.get(0));
    assertEquals(List.of(second), batches.get(1));
}
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
mvn test -Dtest=PlanExecuteAgentTest#splitsParallelTasksWhenResourceLocksConflict -DskipTests=false
```

Expected: compilation failure because method does not exist.

- [ ] **Step 3: Implement `PlanResourceLock`**

Create:

```java
package com.tcode.plan;

import java.util.LinkedHashSet;
import java.util.Set;

public record PlanResourceLock(String value) {
    public PlanResourceLock {
        value = value == null ? "" : value.trim();
    }

    public static Set<String> infer(Task task) {
        Set<String> locks = new LinkedHashSet<>();
        if (task == null) {
            return locks;
        }
        locks.addAll(task.getResourceLocks());
        if (task.getType() == Task.TaskType.COMMAND) {
            locks.add("tool:shell");
        }
        if (task.getType() == Task.TaskType.FILE_WRITE) {
            locks.add("tool:file-write");
        }
        return locks;
    }
}
```

- [ ] **Step 4: Implement safe batch splitting**

Add to `PlanExecuteAgent`:

```java
List<List<Task>> splitIntoResourceSafeBatches(List<Task> executableTasks) {
    List<List<Task>> batches = new ArrayList<>();
    for (Task task : executableTasks) {
        Set<String> taskLocks = PlanResourceLock.infer(task);
        boolean placed = false;
        for (List<Task> batch : batches) {
            Set<String> batchLocks = batch.stream()
                    .flatMap(existing -> PlanResourceLock.infer(existing).stream())
                    .collect(Collectors.toSet());
            if (Collections.disjoint(batchLocks, taskLocks)) {
                batch.add(task);
                placed = true;
                break;
            }
        }
        if (!placed) {
            List<Task> newBatch = new ArrayList<>();
            newBatch.add(task);
            batches.add(newBatch);
        }
    }
    return batches;
}
```

Then in `executePlan()`, replace one call to `executeTaskBatch(plan, executableTasks, streamState)` with:

```java
List<TaskExecutionResult> batchResults = new ArrayList<>();
for (List<Task> safeBatch : splitIntoResourceSafeBatches(executableTasks)) {
    batchResults.addAll(executeTaskBatch(plan, safeBatch, streamState));
}
```

- [ ] **Step 5: Run tests**

Run:

```bash
mvn test -Dtest=PlanExecuteAgentTest,ExecutionPlanTest -DskipTests=false
```

Expected: all pass.

---

### Task 5: Classify Failures Before Retry Or Replan

**Files:**
- Create: `src/main/java/com/tcode/plan/PlanFailureClassifier.java`
- Modify: `src/main/java/com/tcode/agent/PlanExecuteAgent.java`
- Test: `src/test/java/com/tcode/plan/PlanFailureClassifierTest.java`

- [ ] **Step 1: Write failure classifier tests**

Create:

```java
package com.tcode.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanFailureClassifierTest {

    @Test
    void classifiesTimeoutAsRetryable() {
        assertEquals(PlanFailureClassifier.Action.RETRY_TASK,
                new PlanFailureClassifier().classify(new RuntimeException("timeout while executing tool")));
    }

    @Test
    void classifiesPolicyDenialAsStop() {
        assertEquals(PlanFailureClassifier.Action.STOP,
                new PlanFailureClassifier().classify(new RuntimeException("策略拒绝: command denied")));
    }

    @Test
    void classifiesDependencyDeadEndAsReplan() {
        assertEquals(PlanFailureClassifier.Action.REPLAN,
                new PlanFailureClassifier().classify(new RuntimeException("未满足依赖，计划无法继续")));
    }
}
```

- [ ] **Step 2: Run classifier test and verify it fails**

Run:

```bash
mvn test -Dtest=PlanFailureClassifierTest -DskipTests=false
```

Expected: compilation failure because classifier does not exist.

- [ ] **Step 3: Implement classifier**

Create:

```java
package com.tcode.plan;

import java.util.Locale;

public final class PlanFailureClassifier {
    public enum Action {
        RETRY_TASK,
        REPLAN,
        STOP
    }

    public Action classify(Exception error) {
        String message = error == null || error.getMessage() == null
                ? ""
                : error.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("timeout") || message.contains("timed out") || message.contains("超时")) {
            return Action.RETRY_TASK;
        }
        if (message.contains("策略拒绝") || message.contains("policy") || message.contains("denied")) {
            return Action.STOP;
        }
        if (message.contains("依赖") || message.contains("dependency") || message.contains("计划")) {
            return Action.REPLAN;
        }
        return Action.REPLAN;
    }
}
```

- [ ] **Step 4: Wire classifier into failed task handling**

Add field:

```java
private final PlanFailureClassifier failureClassifier = new PlanFailureClassifier();
```

In failed task branch of `executePlan()`, replace progress-only logic with:

```java
PlanFailureClassifier.Action action = failureClassifier.classify(error);
if (action == PlanFailureClassifier.Action.RETRY_TASK) {
    task.setStatus(Task.TaskStatus.PENDING);
    task.setError(null);
    continue;
}
if (action == PlanFailureClassifier.Action.REPLAN || plan.getProgress() < 0.5) {
    out.println("🔧 尝试重新规划...\n");
    ExecutionPlan replanned = planner.replan(plan, error.getMessage());
    return reviewAndExecutePlan(replanned, streamState).result();
}
```

Keep `STOP` as the default fallthrough to final partial failure.

- [ ] **Step 5: Run tests**

Run:

```bash
mvn test -Dtest=PlanFailureClassifierTest,PlanExecuteAgentTest -DskipTests=false
```

Expected: all pass.

---

### Task 6: Add In-Memory Plan Run Trace

**Files:**
- Create: `src/main/java/com/tcode/plan/PlanRunTrace.java`
- Modify: `src/main/java/com/tcode/agent/PlanExecuteAgent.java`
- Test: `src/test/java/com/tcode/agent/PlanExecuteAgentTest.java`

- [ ] **Step 1: Write trace test**

Add to `PlanExecuteAgentTest`:

```java
@Test
void recordsPlanAndTaskTraceEvents() throws Exception {
    StubGLMClient llmClient = new StubGLMClient(List.of(
            new LlmClient.ChatResponse("assistant", "done", null, 10, 5)
    ));
    PlanExecuteAgent agent = new PlanExecuteAgent(
            llmClient,
            new ToolRegistry(),
            new StubPlanner(llmClient),
            null,
            (goal, plan) -> PlanExecuteAgent.PlanReviewDecision.execute()
    );

    agent.run("read file");

    assertTrue(agent.getLastTrace().events().stream().anyMatch(event -> event.type().equals("plan.created")));
    assertTrue(agent.getLastTrace().events().stream().anyMatch(event -> event.type().equals("task.started")));
    assertTrue(agent.getLastTrace().events().stream().anyMatch(event -> event.type().equals("task.completed")));
}
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
mvn test -Dtest=PlanExecuteAgentTest#recordsPlanAndTaskTraceEvents -DskipTests=false
```

Expected: compilation failure because trace API does not exist.

- [ ] **Step 3: Implement trace object**

Create:

```java
package com.tcode.plan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlanRunTrace {
    private final List<Event> events = new ArrayList<>();

    public synchronized void record(String type, String taskId, Map<String, String> attributes) {
        events.add(new Event(Instant.now().toString(), type, taskId,
                attributes == null ? Map.of() : Map.copyOf(attributes)));
    }

    public synchronized List<Event> events() {
        return List.copyOf(events);
    }

    public record Event(String timestamp, String type, String taskId, Map<String, String> attributes) {
    }
}
```

- [ ] **Step 4: Record trace events in `PlanExecuteAgent`**

Add field:

```java
private PlanRunTrace lastTrace = new PlanRunTrace();
```

Add getter:

```java
public PlanRunTrace getLastTrace() {
    return lastTrace;
}
```

At start of `runWithPlan()`:

```java
lastTrace = new PlanRunTrace();
ExecutionPlan plan = planner.createPlan(goal);
lastTrace.record("plan.created", null, Map.of(
        "planId", plan.getId(),
        "taskCount", String.valueOf(plan.getAllTasks().size()),
        "risk", plan.getEstimate() == null ? "UNKNOWN" : plan.getEstimate().riskLevel().name(),
        "estimatedMinutes", plan.getEstimate() == null ? "0" : String.valueOf(plan.getEstimate().estimatedMinutes())));
```

When marking a task started:

```java
lastTrace.record("task.started", task.getId(), Map.of("type", task.getType().name()));
```

When marking completed:

```java
lastTrace.record("task.completed", task.getId(), Map.of());
```

When marking failed:

```java
lastTrace.record("task.failed", task.getId(), Map.of("error", error.getMessage()));
```

In `executeToolCalls()` before or after execution:

```java
lastTrace.record("tool.calls", taskId, Map.of("count", String.valueOf(invocations.size())));
```

- [ ] **Step 5: Run tests**

Run:

```bash
mvn test -Dtest=PlanExecuteAgentTest -DskipTests=false
```

Expected: all pass.

---

## Documentation Updates

After Tasks 1-6 pass:

- [ ] Update `AGENTS.md`
  - Add that Plan-and-Execute now validates plans before execution.
  - Add that Plan-and-Execute shows a conservative estimate before execution.
  - Add that parallel execution uses resource locks.
- [ ] Update `docs/agents-reference.md`
  - Add a section under `PlanExecuteAgent.java` describing validator, estimator, resource locking, failure classifier, and trace.
- [ ] Update `README.md` only if user-facing `/plan` behavior changes.

Validation command:

```bash
mvn test -Dtest=PlanValidatorTest,PlanEstimatorTest,PlanFailureClassifierTest,PlannerTest,ExecutionPlanTest,PlanExecuteAgentTest,CliPlanReviewHandlerTest -DskipTests=false
```

Expected: all pass.

---

## Execution Order

Recommended order:

1. Task 1: Plan validation
2. Task 2: Optional task metadata
3. Task 3: Plan estimation
4. Task 4: Resource-aware batching
5. Task 5: Failure classification
6. Task 6: Trace
7. Documentation updates

This order keeps each change independently testable and avoids changing runtime behavior before validation and metadata foundations exist.

## Deferred Work

Do not implement these in this first hardening pass:

- Durable `PlanRunStore`
- `/plan resume`
- Full trajectory evaluation harness
- Persistent trace JSONL
- CLI UI for validation warnings

Those should be a second plan after the hardening primitives land.
