package com.tcode.plan;

public record PlanValidationIssue(Severity severity, String code, String taskId, String message) {
    public enum Severity {
        ERROR,
        WARNING
    }
}
