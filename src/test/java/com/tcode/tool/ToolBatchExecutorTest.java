package com.tcode.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolBatchExecutorTest {

    @Test
    void runsToolsInParallelAndKeepsInputOrder() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger current = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        ToolBatchExecutor executor = new ToolBatchExecutor(5, 4);

        List<ToolRegistry.ToolExecutionResult> results = executor.execute(List.of(
                new ToolRegistry.ToolInvocation("one", "first", "{}"),
                new ToolRegistry.ToolInvocation("two", "second", "{}")
        ), invocation -> {
            int now = current.incrementAndGet();
            peak.updateAndGet(old -> Math.max(old, now));
            bothStarted.countDown();
            try {
                assertTrue(bothStarted.await(2, TimeUnit.SECONDS));
                return ToolOutput.text("done-" + invocation.name());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolOutput.text("interrupted");
            } finally {
                current.decrementAndGet();
            }
        });

        assertEquals(2, peak.get());
        assertEquals("one", results.get(0).id());
        assertEquals("done-first", results.get(0).result());
        assertEquals("two", results.get(1).id());
        assertEquals("done-second", results.get(1).result());
    }

    @Test
    void retriesRetryableFailuresOnce() {
        AtomicInteger attempts = new AtomicInteger();
        ToolBatchExecutor executor = new ToolBatchExecutor(5, 4);

        List<ToolRegistry.ToolExecutionResult> results = executor.execute(List.of(
                new ToolRegistry.ToolInvocation("one", "flaky", "{}")
        ), invocation -> attempts.incrementAndGet() == 1
                ? ToolOutput.failure("temporary network issue", ToolErrorCode.EXTERNAL_SERVICE_ERROR, true)
                : ToolOutput.text("ok"));

        assertEquals(2, attempts.get());
        assertEquals("ok", results.get(0).result());
        assertEquals(ToolCallStatus.SUCCEEDED, results.get(0).status());
    }

    @Test
    void keepsStructuredFailureWhenRetryStillFails() {
        ToolBatchExecutor executor = new ToolBatchExecutor(5, 4);

        List<ToolRegistry.ToolExecutionResult> results = executor.execute(List.of(
                new ToolRegistry.ToolInvocation("one", "flaky", "{}")
        ), invocation -> ToolOutput.failure("temporary network issue", ToolErrorCode.EXTERNAL_SERVICE_ERROR, true));

        assertEquals(ToolCallStatus.FAILED, results.get(0).status());
        assertEquals(ToolErrorCode.EXTERNAL_SERVICE_ERROR, results.get(0).errorCode());
        assertTrue(results.get(0).result().contains("EXTERNAL_SERVICE_ERROR"));
    }

    @Test
    void serializesConflictingWritesToSameFile() {
        AtomicInteger current = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        ToolBatchExecutor executor = new ToolBatchExecutor(5, 4);

        List<ToolRegistry.ToolExecutionResult> results = executor.execute(List.of(
                new ToolRegistry.ToolInvocation("one", "write_file", "{\"path\":\"src/App.java\",\"content\":\"a\"}"),
                new ToolRegistry.ToolInvocation("two", "write_file", "{\"path\":\"src/App.java\",\"content\":\"b\"}")
        ), invocation -> {
            int now = current.incrementAndGet();
            peak.updateAndGet(old -> Math.max(old, now));
            try {
                Thread.sleep(100);
                return ToolOutput.text("done-" + invocation.id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolOutput.failure("interrupted", ToolErrorCode.CANCELLED, false);
            } finally {
                current.decrementAndGet();
            }
        });

        assertEquals(1, peak.get());
        assertEquals("done-one", results.get(0).result());
        assertEquals("done-two", results.get(1).result());
    }

    @Test
    void allowsIndependentReadsInSameBatch() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger current = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        ToolBatchExecutor executor = new ToolBatchExecutor(5, 4);

        List<ToolRegistry.ToolExecutionResult> results = executor.execute(List.of(
                new ToolRegistry.ToolInvocation("one", "read_file", "{\"path\":\"src/A.java\"}"),
                new ToolRegistry.ToolInvocation("two", "read_file", "{\"path\":\"src/B.java\"}")
        ), invocation -> {
            int now = current.incrementAndGet();
            peak.updateAndGet(old -> Math.max(old, now));
            bothStarted.countDown();
            try {
                assertTrue(bothStarted.await(2, TimeUnit.SECONDS));
                return ToolOutput.text("done-" + invocation.id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolOutput.failure("interrupted", ToolErrorCode.CANCELLED, false);
            } finally {
                current.decrementAndGet();
            }
        });

        assertEquals(2, peak.get());
        assertEquals("done-one", results.get(0).result());
        assertEquals("done-two", results.get(1).result());
    }

    @Test
    void serializesShellWithWorkspaceReads() {
        AtomicInteger current = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        ToolBatchExecutor executor = new ToolBatchExecutor(5, 4);

        List<ToolRegistry.ToolExecutionResult> results = executor.execute(List.of(
                new ToolRegistry.ToolInvocation("one", "execute_command", "{\"command\":\"mvn test\"}"),
                new ToolRegistry.ToolInvocation("two", "grep_code", "{\"pattern\":\"ToolBatchExecutor\"}")
        ), invocation -> {
            int now = current.incrementAndGet();
            peak.updateAndGet(old -> Math.max(old, now));
            try {
                Thread.sleep(100);
                return ToolOutput.text("done-" + invocation.id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolOutput.failure("interrupted", ToolErrorCode.CANCELLED, false);
            } finally {
                current.decrementAndGet();
            }
        });

        assertEquals(1, peak.get());
        assertEquals("done-one", results.get(0).result());
        assertEquals("done-two", results.get(1).result());
    }
}
