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
                new PlanFailureClassifier().classify(new RuntimeException("policy denied: command denied")));
    }

    @Test
    void classifiesDependencyDeadEndAsReplan() {
        assertEquals(PlanFailureClassifier.Action.REPLAN,
                new PlanFailureClassifier().classify(new RuntimeException("dependency unmet, plan cannot continue")));
    }
}
