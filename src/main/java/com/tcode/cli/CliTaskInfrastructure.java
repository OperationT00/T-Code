package com.tcode.cli;

import com.tcode.runtime.task.DurableTaskManager;
import com.tcode.runtime.task.TaskRunner;

import java.util.function.Consumer;

record CliTaskInfrastructure(DurableTaskManager taskManager) implements AutoCloseable {

    static CliTaskInfrastructure start(TaskRunner runner, Consumer<Thread> shutdownHookRegistrar) {
        try {
            DurableTaskManager taskManager = DurableTaskManager.openDefault(runner);
            taskManager.start();
            shutdownHookRegistrar.accept(new Thread(taskManager::close, "tcode-task-shutdown"));
            return new CliTaskInfrastructure(taskManager);
        } catch (Exception e) {
            throw new IllegalStateException("后台任务管理器初始化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        taskManager.close();
    }
}
