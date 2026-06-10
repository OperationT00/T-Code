package com.tcode.plan;

import java.util.ArrayList;
import java.util.List;

public record PlanValidationResult(List<PlanValidationIssue> issues) {
    public PlanValidationResult {
        issues = List.copyOf(issues == null ? List.of() : issues);
    }

    public static PlanValidationResult ok() {
        return new PlanValidationResult(List.of());
    }

    public boolean hasErrors() {
        return issues.stream()
                .anyMatch(issue -> issue.severity() == PlanValidationIssue.Severity.ERROR);
    }

    public String formatErrors() {
        List<String> lines = new ArrayList<>();
        for (PlanValidationIssue issue : issues) {
            if (issue.severity() == PlanValidationIssue.Severity.ERROR) {
                lines.add(issue.code()
                        + (issue.taskId() == null ? "" : " [" + issue.taskId() + "]")
                        + ": " + issue.message());
            }
        }
        return String.join("\n", lines);
    }
}
