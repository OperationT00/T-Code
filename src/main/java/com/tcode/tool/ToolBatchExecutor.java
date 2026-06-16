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
    private final int maxRetryAttempts;

    public ToolBatchExecutor(long timeoutSeconds, int maxParallelTools) {
        this(timeoutSeconds, maxParallelTools, 1);
    }

    public ToolBatchExecutor(long timeoutSeconds, int maxParallelTools, int maxRetryAttempts) {
        this.timeoutSeconds = timeoutSeconds;
        this.maxParallelTools = maxParallelTools;
        this.maxRetryAttempts = Math.max(maxRetryAttempts, 0);
    }

    public List<ToolRegistry.ToolExecutionResult> execute(
            List<ToolRegistry.ToolInvocation> invocations,
            Function<ToolRegistry.ToolInvocation, ToolOutput> toolExecutor) {
        if (invocations == null || invocations.isEmpty()) {
            return List.of();
        }
        List<List<ToolRegistry.ToolInvocation>> batches = ToolResourceScheduler.splitIntoBatches(invocations);
        if (batches.size() > 1) {
            List<ToolRegistry.ToolExecutionResult> results = new ArrayList<>();
            for (List<ToolRegistry.ToolInvocation> batch : batches) {
                results.addAll(executeBatch(batch, toolExecutor));
            }
            return results;
        }
        return executeBatch(invocations, toolExecutor);
    }

    private List<ToolRegistry.ToolExecutionResult> executeBatch(
            List<ToolRegistry.ToolInvocation> invocations,
            Function<ToolRegistry.ToolInvocation, ToolOutput> toolExecutor) {
        if (CancellationContext.isCancelled()) {
            return invocations.stream()
                    .map(invocation -> ToolRegistry.ToolExecutionResult.cancelled(invocation))
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
                    results.add(ToolRegistry.ToolExecutionResult.cancelled(invocation));
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    results.add(ToolRegistry.ToolExecutionResult.failed(invocation,
                            cause == null || cause.getMessage() == null ? "unknown error" : cause.getMessage()));
                }
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return invocations.stream()
                    .map(ToolRegistry.ToolExecutionResult::cancelled)
                    .toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private ToolRegistry.ToolExecutionResult executeOne(
            ToolRegistry.ToolInvocation invocation,
            Function<ToolRegistry.ToolInvocation, ToolOutput> toolExecutor) {
        if (CancellationContext.isCancelled()) {
            return ToolRegistry.ToolExecutionResult.cancelled(invocation);
        }
        long startedAt = System.nanoTime();
        ToolOutput output = null;
        int attempts = 0;
        int maxAttempts = 1 + maxRetryAttempts;
        while (attempts < maxAttempts) {
            attempts++;
            output = toolExecutor.apply(invocation);
            if (output == null || output.succeeded() || !output.retryable()) {
                break;
            }
        }
        if (output == null) {
            output = ToolOutput.text("");
        }
        long elapsed = elapsedMillis(startedAt);
        return ToolRegistry.ToolExecutionResult.completed(
                invocation,
                output.withTiming(elapsed).withAttempts(attempts),
                elapsed);
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
