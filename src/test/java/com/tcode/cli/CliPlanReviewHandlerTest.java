package com.tcode.cli;

import com.tcode.agent.PlanExecuteAgent;
import com.tcode.plan.ExecutionPlan;
import com.tcode.plan.PlanEstimate;
import com.tcode.plan.Task;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliPlanReviewHandlerTest {
    @Test
    void mapsSupplementDecisionToAgentDecision() {
        PlanExecuteAgent.PlanReviewDecision decision = CliPlanReviewHandler.mapDecision(
                PlanReviewInputParser.parse("check README first"));

        assertEquals(PlanExecuteAgent.PlanReviewAction.SUPPLEMENT, decision.action());
        assertEquals("check README first", decision.feedback());
    }

    @Test
    void printsEstimateWhenReviewingPlan() {
        ExecutionPlan plan = new ExecutionPlan("plan_1", "inspect project");
        plan.addTask(new Task("task_1", "read pom", Task.TaskType.FILE_READ));
        plan.computeExecutionOrder();
        plan.setEstimate(new PlanEstimate(1, 1, 1, 2, PlanEstimate.RiskLevel.LOW, "normal"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        CliPlanReviewHandler.printPlanSummaryForReview(
                plan,
                new PrintStream(output, true, StandardCharsets.UTF_8));

        String rendered = output.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("Estimate"));
        assertTrue(rendered.contains("risk=LOW"));
    }
}
