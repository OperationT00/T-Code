package com.tcode.plan;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class PlanRecoveryBudget {
    private final int maxRetriesPerTask;
    private final int maxReplansPerRun;
    private final int maxTotalRecoveryActions;
    private final int maxConsecutiveSameFailure;
    private final Map<String, Integer> retryAttemptsByTask = new HashMap<>();
    private int replanAttempts;
    private int totalRecoveryActions;
    private String lastFailureFingerprint = "";
    private int consecutiveSameFailureCount;

    public PlanRecoveryBudget(int maxRetriesPerTask, int maxReplansPerRun,
                              int maxTotalRecoveryActions, int maxConsecutiveSameFailure) {
        this.maxRetriesPerTask = Math.max(0, maxRetriesPerTask);
        this.maxReplansPerRun = Math.max(0, maxReplansPerRun);
        this.maxTotalRecoveryActions = Math.max(0, maxTotalRecoveryActions);
        this.maxConsecutiveSameFailure = Math.max(1, maxConsecutiveSameFailure);
    }

    public static PlanRecoveryBudget defaults() {
        return new PlanRecoveryBudget(
                readIntConfig("tcode.plan.recovery.maxRetriesPerTask",
                        "TCODE_PLAN_RECOVERY_MAX_RETRIES_PER_TASK", 1),
                readIntConfig("tcode.plan.recovery.maxReplansPerRun",
                        "TCODE_PLAN_RECOVERY_MAX_REPLANS_PER_RUN", 2),
                readIntConfig("tcode.plan.recovery.maxTotalActions",
                        "TCODE_PLAN_RECOVERY_MAX_TOTAL_ACTIONS", 5),
                readIntConfig("tcode.plan.recovery.maxConsecutiveSameFailure",
                        "TCODE_PLAN_RECOVERY_MAX_CONSECUTIVE_SAME_FAILURE", 2));
    }

    public boolean canRetry(Task task, Exception error) {
        return retryAttempts(task) < maxRetriesPerTask
                && totalRecoveryActions < maxTotalRecoveryActions
                && !isRepeatedFailure(error);
    }

    public void recordRetry(Task task, Exception error) {
        retryAttemptsByTask.merge(taskKey(task), 1, Integer::sum);
        recordRecoveryAction(error);
    }

    public int retryAttempts(Task task) {
        return retryAttemptsByTask.getOrDefault(taskKey(task), 0);
    }

    public boolean canReplan(Exception error) {
        return replanAttempts < maxReplansPerRun
                && totalRecoveryActions < maxTotalRecoveryActions
                && !isRepeatedFailure(error);
    }

    public void recordReplan(Exception error) {
        replanAttempts++;
        recordRecoveryAction(error);
    }

    public int replanAttempts() {
        return replanAttempts;
    }

    public int totalRecoveryActions() {
        return totalRecoveryActions;
    }

    public boolean isRepeatedFailure(Exception error) {
        String fingerprint = fingerprint(error);
        return !fingerprint.isBlank()
                && fingerprint.equals(lastFailureFingerprint)
                && consecutiveSameFailureCount >= maxConsecutiveSameFailure;
    }

    private void recordRecoveryAction(Exception error) {
        totalRecoveryActions++;
        String fingerprint = fingerprint(error);
        if (fingerprint.isBlank()) {
            lastFailureFingerprint = "";
            consecutiveSameFailureCount = 0;
            return;
        }
        if (fingerprint.equals(lastFailureFingerprint)) {
            consecutiveSameFailureCount++;
        } else {
            lastFailureFingerprint = fingerprint;
            consecutiveSameFailureCount = 1;
        }
    }

    private static String taskKey(Task task) {
        if (task == null) {
            return "";
        }
        return task.getType().name() + ":" + task.getId() + ":" + task.getDescription();
    }

    private static String fingerprint(Exception error) {
        if (error == null || error.getMessage() == null) {
            return "";
        }
        String normalized = error.getMessage().toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replaceAll("[A-Za-z]:/[^\\s]+", "<path>")
                .replaceAll("/[^\\s]+", "<path>")
                .replaceAll("\\d+", "<n>")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) : normalized;
    }

    private static int readIntConfig(String propertyName, String envName, int defaultValue) {
        String configured = System.getProperty(propertyName);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(envName);
        }
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(configured.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
