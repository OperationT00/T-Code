package com.tcode.plan;

import java.util.Locale;

public final class PlanFailureClassifier {
    public enum Action {
        RETRY_TASK,
        REPLAN,
        STOP
    }

    public record RecoveryDecision(Action action, String reason, boolean userInterventionRecommended) {
    }

    public RecoveryDecision classify(Exception error) {
        String message = error == null || error.getMessage() == null
                ? ""
                : error.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("timeout") || message.contains("timed out") || message.contains("超时")) {
            return new RecoveryDecision(Action.RETRY_TASK, "transient timeout", false);
        }
        if (message.contains("策略拒绝") || message.contains("policy") || message.contains("denied")) {
            return new RecoveryDecision(Action.STOP, "policy denied", true);
        }
        if (message.contains("依赖") || message.contains("dependency")
                || message.contains("计划") || message.contains("plan")) {
            return new RecoveryDecision(Action.REPLAN, "plan dependency failure", false);
        }
        return new RecoveryDecision(Action.REPLAN, "unclassified execution failure", false);
    }
}
