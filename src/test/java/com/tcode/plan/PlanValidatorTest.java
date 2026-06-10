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
