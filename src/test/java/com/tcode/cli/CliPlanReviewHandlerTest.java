package com.tcode.cli;

import com.tcode.agent.PlanExecuteAgent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliPlanReviewHandlerTest {
    @Test
    void mapsSupplementDecisionToAgentDecision() {
        PlanExecuteAgent.PlanReviewDecision decision = CliPlanReviewHandler.mapDecision(
                PlanReviewInputParser.parse("先检查 README"));

        assertEquals(PlanExecuteAgent.PlanReviewAction.SUPPLEMENT, decision.action());
        assertEquals("先检查 README", decision.feedback());
    }
}
