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
        if (message.contains("依赖") || message.contains("dependency") || message.contains("计划") || message.contains("plan")) {
            return Action.REPLAN;
        }
        return Action.REPLAN;
    }
}
