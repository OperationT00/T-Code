package com.tcode.plan;

import java.util.LinkedHashSet;
import java.util.Set;

public record PlanResourceLock(String value) {
    public PlanResourceLock {
        value = value == null ? "" : value.trim();
    }

    public static Set<String> infer(Task task) {
        Set<String> locks = new LinkedHashSet<>();
        if (task == null) {
            return locks;
        }
        locks.addAll(task.getResourceLocks());
        if (task.getType() == Task.TaskType.COMMAND) {
            locks.add("tool:shell");
        }
        if (task.getType() == Task.TaskType.FILE_WRITE) {
            locks.add("tool:file-write");
        }
        return locks;
    }
}
