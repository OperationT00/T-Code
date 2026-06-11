package com.tcode.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanRecoveryBudgetTest {

    @Test
    void limitsRetriesPerTaskAcrossTheRun() {
        PlanRecoveryBudget budget = PlanRecoveryBudget.defaults();
        Task task = new Task("task_1", "run flaky command", Task.TaskType.COMMAND);
        Exception timeout = new RuntimeException("timeout while executing command");

        assertTrue(budget.canRetry(task, timeout));
        budget.recordRetry(task, timeout);

        assertFalse(budget.canRetry(task, timeout));
        assertEquals(1, budget.retryAttempts(task));
    }

    @Test
    void limitsReplansPerRun() {
        PlanRecoveryBudget budget = PlanRecoveryBudget.defaults();
        Exception planError = new RuntimeException("dependency unmet");

        assertTrue(budget.canReplan(planError));
        budget.recordReplan(planError);
        assertTrue(budget.canReplan(planError));
        budget.recordReplan(planError);

        assertFalse(budget.canReplan(planError));
        assertEquals(2, budget.replanAttempts());
    }

    @Test
    void detectsRepeatedFailuresAcrossRecoveryActions() {
        PlanRecoveryBudget budget = PlanRecoveryBudget.defaults();
        Exception first = new RuntimeException("Dependency unmet at line 42");
        Exception second = new RuntimeException("dependency unmet at line 99");

        budget.recordReplan(first);
        assertFalse(budget.isRepeatedFailure(second));

        budget.recordReplan(second);
        assertTrue(budget.isRepeatedFailure(new RuntimeException("dependency unmet at line 120")));
    }

    @Test
    void readsDefaultsFromSystemProperties() {
        String oldRetries = System.getProperty("tcode.plan.recovery.maxRetriesPerTask");
        String oldReplans = System.getProperty("tcode.plan.recovery.maxReplansPerRun");
        try {
            System.setProperty("tcode.plan.recovery.maxRetriesPerTask", "2");
            System.setProperty("tcode.plan.recovery.maxReplansPerRun", "1");
            PlanRecoveryBudget budget = PlanRecoveryBudget.defaults();
            Task task = new Task("task_1", "flaky", Task.TaskType.COMMAND);
            Exception timeout = new RuntimeException("timeout");

            assertTrue(budget.canRetry(task, timeout));
            budget.recordRetry(task, timeout);
            assertTrue(budget.canRetry(task, new RuntimeException("timeout in another place")));
            budget.recordRetry(task, new RuntimeException("timeout in another place"));
            assertFalse(budget.canRetry(task, new RuntimeException("timeout in final place")));

            assertTrue(budget.canReplan(new RuntimeException("dependency one")));
            budget.recordReplan(new RuntimeException("dependency one"));
            assertFalse(budget.canReplan(new RuntimeException("dependency two")));
        } finally {
            restoreProperty("tcode.plan.recovery.maxRetriesPerTask", oldRetries);
            restoreProperty("tcode.plan.recovery.maxReplansPerRun", oldReplans);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
