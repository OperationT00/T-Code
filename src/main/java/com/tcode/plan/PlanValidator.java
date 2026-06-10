package com.tcode.plan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PlanValidator {

    public PlanValidationResult validate(ExecutionPlan plan) {
        if (plan == null) {
            return new PlanValidationResult(List.of(error("NULL_PLAN", null, "plan is null")));
        }

        List<PlanValidationIssue> issues = new ArrayList<>();
        if (plan.getAllTasks().isEmpty()) {
            issues.add(error("EMPTY_PLAN", null, "plan has no tasks"));
        }

        for (Task task : plan.getAllTasks()) {
            if (task.getDescription() == null || task.getDescription().trim().isEmpty()) {
                issues.add(error("EMPTY_TASK_DESCRIPTION", task.getId(), "task description must not be blank"));
            }
            for (String depId : task.getDependencies()) {
                if (plan.getTask(depId) == null) {
                    issues.add(error("UNKNOWN_DEPENDENCY", task.getId(), "dependency does not exist: " + depId));
                }
            }
            if (requiresVerification(task) && !hasVerificationDependent(plan, task)) {
                issues.add(warning("MISSING_VERIFICATION", task.getId(),
                        "write or command task should be followed by a verification task"));
            }
        }

        if (!plan.computeExecutionOrder()) {
            issues.add(error("CYCLIC_DEPENDENCY", null, "plan contains cyclic dependencies"));
        }

        return new PlanValidationResult(issues);
    }

    private boolean requiresVerification(Task task) {
        return task.getType() == Task.TaskType.FILE_WRITE || task.getType() == Task.TaskType.COMMAND;
    }

    private boolean hasVerificationDependent(ExecutionPlan plan, Task task) {
        Set<String> visited = new HashSet<>();
        return hasVerificationDependent(plan, task.getId(), visited);
    }

    private boolean hasVerificationDependent(ExecutionPlan plan, String taskId, Set<String> visited) {
        if (!visited.add(taskId)) {
            return false;
        }
        Task task = plan.getTask(taskId);
        if (task == null) {
            return false;
        }
        for (String dependentId : task.getDependents()) {
            Task dependent = plan.getTask(dependentId);
            if (dependent == null) {
                continue;
            }
            if (dependent.getType() == Task.TaskType.VERIFICATION) {
                return true;
            }
            if (hasVerificationDependent(plan, dependentId, visited)) {
                return true;
            }
        }
        return false;
    }

    private static PlanValidationIssue error(String code, String taskId, String message) {
        return new PlanValidationIssue(PlanValidationIssue.Severity.ERROR, code, taskId, message);
    }

    private static PlanValidationIssue warning(String code, String taskId, String message) {
        return new PlanValidationIssue(PlanValidationIssue.Severity.WARNING, code, taskId, message);
    }
}
