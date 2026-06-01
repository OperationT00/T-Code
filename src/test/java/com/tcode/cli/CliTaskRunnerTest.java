package com.tcode.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliTaskRunnerTest {
    @Test
    void returnsTaskResultWithoutInteractiveTerminal() {
        assertEquals("ok", CliTaskRunner.run(null, () -> "ok"));
    }

    @Test
    void formatsTaskFailureForCliTranscript() {
        assertEquals("❌ 执行失败: boom", CliTaskRunner.run(null, () -> {
            throw new IllegalStateException("boom");
        }));
    }
}
