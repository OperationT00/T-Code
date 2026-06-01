package com.tcode.cli;

import com.tcode.runtime.task.DurableTask;
import com.tcode.runtime.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliTaskInfrastructureTest {

    @Test
    void opensStartsAndRegistersShutdownHook(@TempDir Path tempDir) throws Exception {
        String original = System.getProperty("tcode.task.dir");
        System.setProperty("tcode.task.dir", tempDir.toString());
        List<Thread> shutdownHooks = new ArrayList<>();
        try (CliTaskInfrastructure infrastructure =
                     CliTaskInfrastructure.start(prompt -> "reply:" + prompt, shutdownHooks::add)) {
            DurableTask task = infrastructure.taskManager().enqueue("hello");

            assertEquals("tcode-task-shutdown", shutdownHooks.get(0).getName());
            assertEquals("reply:hello", waitForTerminal(infrastructure, task.id()).result());
        } finally {
            if (original == null) {
                System.clearProperty("tcode.task.dir");
            } else {
                System.setProperty("tcode.task.dir", original);
            }
        }
    }

    private static DurableTask waitForTerminal(CliTaskInfrastructure infrastructure, String id)
            throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            DurableTask task = infrastructure.taskManager().find(id).orElseThrow();
            if (task.status() == TaskStatus.COMPLETED) {
                return task;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("task did not complete");
    }
}
