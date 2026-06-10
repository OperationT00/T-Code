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
