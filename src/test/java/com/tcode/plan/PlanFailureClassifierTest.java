package com.tcode.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanFailureClassifierTest {

    @Test
    void classifiesTimeoutAsRetryable() {
        PlanFailureClassifier.RecoveryDecision decision =
                new PlanFailureClassifier().classify(new RuntimeException("timeout while executing tool"));

        assertEquals(PlanFailureClassifier.Action.RETRY_TASK, decision.action());
        assertEquals("transient timeout", decision.reason());
    }

    @Test
    void classifiesPolicyDenialAsStop() {
        PlanFailureClassifier.RecoveryDecision decision =
                new PlanFailureClassifier().classify(new RuntimeException("policy denied: command denied"));

        assertEquals(PlanFailureClassifier.Action.STOP, decision.action());
        assertEquals("policy denied", decision.reason());
        assertEquals(true, decision.userInterventionRecommended());
    }

    @Test
    void classifiesDependencyDeadEndAsReplan() {
        PlanFailureClassifier.RecoveryDecision decision =
                new PlanFailureClassifier().classify(new RuntimeException("dependency unmet, plan cannot continue"));

        assertEquals(PlanFailureClassifier.Action.REPLAN, decision.action());
        assertEquals("plan dependency failure", decision.reason());
    }
}
