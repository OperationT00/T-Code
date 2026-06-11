package com.tcode.plan;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanResourceLockTest {

    @Test
    void normalizesFileLocks() {
        assertEquals("file:src/App.java", PlanResourceLock.normalize(" FILE:.\\src\\App.java "));
        assertEquals("file:src/App.java", PlanResourceLock.normalize("./src/App.java"));
        assertEquals("tool:browser", PlanResourceLock.normalize(" TOOL:Browser "));
    }

    @Test
    void usesSpecificFileLocksWithoutGlobalFileWriteLock() {
        Task task = new Task("task_1", "write app", Task.TaskType.FILE_WRITE);
        task.setResourceLocks(java.util.List.of(" FILE:.\\src\\App.java "));

        Set<String> locks = PlanResourceLock.infer(task);

        assertTrue(locks.contains("file:src/App.java"));
        assertFalse(locks.contains("tool:file-write"));
    }

    @Test
    void fallsBackToGlobalFileWriteLockWhenSpecificFileIsUnknown() {
        Task task = new Task("task_1", "write unknown file", Task.TaskType.FILE_WRITE);

        Set<String> locks = PlanResourceLock.infer(task);

        assertEquals(Set.of("tool:file-write"), locks);
    }

    @Test
    void infersSpecificFileWriteLockFromDescription() {
        Task task = new Task("task_1", "update `src/main/java/com/tcode/App.java`", Task.TaskType.FILE_WRITE);

        Set<String> locks = PlanResourceLock.infer(task);

        assertTrue(locks.contains("file:src/main/java/com/tcode/App.java"));
        assertFalse(locks.contains("tool:file-write"));
    }

    @Test
    void detectsFileDirectoryLockConflicts() {
        assertTrue(PlanResourceLock.conflicts("dir:src/main/java", "file:src/main/java/com/tcode/App.java"));
        assertTrue(PlanResourceLock.conflicts("file:src/main/java/com/tcode/App.java", "dir:src/main/java"));
        assertFalse(PlanResourceLock.conflicts("dir:src/test/java", "file:src/main/java/com/tcode/App.java"));
        assertTrue(PlanResourceLock.conflicts("tool:shell", "tool:shell"));
        assertFalse(PlanResourceLock.conflicts("tool:shell", "tool:browser"));
    }
}
