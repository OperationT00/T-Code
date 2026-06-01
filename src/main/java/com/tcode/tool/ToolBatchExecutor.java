package com.tcode.tool;

import com.tcode.runtime.CancellationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class ToolBatchExecutor {
    private final long timeoutSeconds;
    private final int maxParallelTools;

    public ToolBatchExecutor(long timeoutSeconds, int maxParallelTools) {
        this.timeoutSeconds = timeoutSeconds;
        this.maxParallelTools = maxParallelTools;
    }

    public List<ToolRegistry.ToolExecutionResult> execute(
            List<ToolRegistry.ToolInvocation> invocations,
            Function<ToolRegistry.ToolInvocation, ToolOutput> toolExecutor) {
        if (invocations == null || invocations.isEmpty()) {
            return List.of();
        }
        if (CancellationContext.isCancelled()) {
            return invocations.stream()
                    .map(invocation -> ToolRegistry.ToolExecutionResult.failed(invocation, "用户取消了此次工具调用"))
                    .toList();
        }
        if (invocations.size() == 1) {
            return List.of(executeOne(invocations.get(0), toolExecutor));
        }

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(invocations.size(), maxParallelTools),
                runnable -> {
                    Thread thread = new Thread(runnable, "tcode-tool-executor");
                    thread.setDaemon(true);
                    return thread;
                });
        try {
            List<Callable<ToolRegistry.ToolExecutionResult>> tasks = invocations.stream()
                    .<Callable<ToolRegistry.ToolExecutionResult>>map(invocation ->
                            () -> executeOne(invocation, toolExecutor))
                    .toList();
            List<Future<ToolRegistry.ToolExecutionResult>> futures =
                    executor.invokeAll(tasks, timeoutSeconds, TimeUnit.SECONDS);
            List<ToolRegistry.ToolExecutionResult> results = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                Future<ToolRegistry.ToolExecutionResult> future = futures.get(i);
                ToolRegistry.ToolInvocation invocation = invocations.get(i);
                if (future.isCancelled()) {
                    results.add(ToolRegistry.ToolExecutionResult.timedOut(invocation, timeoutSeconds));
                    continue;
                }
                try {
                    results.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.add(ToolRegistry.ToolExecutionResult.failed(invocation, "工具执行被中断"));
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    results.add(ToolRegistry.ToolExecutionResult.failed(invocation,
                            cause == null || cause.getMessage() == null ? "未知错误" : cause.getMessage()));
                }
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return invocations.stream()
                    .map(invocation -> ToolRegistry.ToolExecutionResult.failed(invocation, "工具批次执行被中断"))
                    .toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private ToolRegistry.ToolExecutionResult executeOne(
            ToolRegistry.ToolInvocation invocation,
            Function<ToolRegistry.ToolInvocation, ToolOutput> toolExecutor) {
        if (CancellationContext.isCancelled()) {
            return ToolRegistry.ToolExecutionResult.failed(invocation, "用户取消了此次工具调用");
        }
        long startedAt = System.nanoTime();
        ToolOutput output = toolExecutor.apply(invocation);
        return ToolRegistry.ToolExecutionResult.completed(invocation, output, elapsedMillis(startedAt));
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
