package com.tcode.plan;

public record PlanEstimate(int taskCount,
                           int batchCount,
                           int effortScore,
                           int estimatedMinutes,
                           RiskLevel riskLevel,
                           String reviewRecommendation) {
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    public String formatForReview() {
        return "Estimate: tasks=" + taskCount
                + ", batches=" + batchCount
                + ", effort=" + effortScore
                + ", minutes~" + estimatedMinutes
                + ", risk=" + riskLevel
                + ", review=" + reviewRecommendation;
    }
}
