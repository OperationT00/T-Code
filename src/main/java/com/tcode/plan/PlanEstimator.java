package com.tcode.plan;

public final class PlanEstimator {

    public PlanEstimate estimate(ExecutionPlan plan) {
        if (plan == null || plan.getAllTasks().isEmpty()) {
            return new PlanEstimate(0, 0, 0, 0, PlanEstimate.RiskLevel.LOW, "none");
        }

        int effort = 0;
        int risk = 0;
        for (Task task : plan.getAllTasks()) {
            effort += effortWeight(task);
            risk += riskWeight(task);
            if (!task.getResourceLocks().isEmpty()) {
                risk += 1;
            }
        }

        int taskCount = plan.getAllTasks().size();
        int batchCount = plan.getExecutionBatches().size();
        int estimatedMinutes = Math.max(1, Math.min(90, effort * 2));
        PlanEstimate.RiskLevel riskLevel = riskLevel(risk, effort, taskCount);
        String recommendation = switch (riskLevel) {
            case LOW -> "normal";
            case MEDIUM -> "review summary";
            case HIGH -> "expand plan before execution";
        };

        return new PlanEstimate(taskCount, batchCount, effort, estimatedMinutes, riskLevel, recommendation);
    }

    private int effortWeight(Task task) {
        return switch (task.getType()) {
            case PLANNING -> 1;
            case FILE_READ -> 1;
            case ANALYSIS -> 2;
            case FILE_WRITE -> 4;
            case COMMAND -> 3;
            case VERIFICATION -> 2;
        };
    }

    private int riskWeight(Task task) {
        return switch (task.getType()) {
            case FILE_WRITE, COMMAND -> 3;
            case VERIFICATION -> 1;
            default -> 0;
        };
    }

    private PlanEstimate.RiskLevel riskLevel(int risk, int effort, int taskCount) {
        if (risk >= 5 || effort >= 10 || taskCount >= 6) {
            return PlanEstimate.RiskLevel.HIGH;
        }
        if (risk >= 2 || effort >= 5 || taskCount >= 3) {
            return PlanEstimate.RiskLevel.MEDIUM;
        }
        return PlanEstimate.RiskLevel.LOW;
    }
}
