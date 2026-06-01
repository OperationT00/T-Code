package com.tcode.tool;

import com.tcode.snapshot.RestoreResult;
import com.tcode.snapshot.SnapshotService;

import java.util.function.Supplier;

public final class SnapshotToolsProvider implements ToolProvider {
    private final Supplier<SnapshotService> snapshotServiceSupplier;

    public SnapshotToolsProvider(Supplier<SnapshotService> snapshotServiceSupplier) {
        this.snapshotServiceSupplier = snapshotServiceSupplier == null ? () -> null : snapshotServiceSupplier;
    }

    @Override
    public void register(ToolRegistrationContext context) {
        context.register(
                "revert_turn",
                "恢复到 Side-Git 记录的最近第 N 个 pre-turn 快照。会先记录 pre-restore 快照；属于高危写入操作，必须经 HITL 审批。",
                context.parameters(
                        context.param("offset", "integer", "要恢复的 pre-turn 快照序号，1 表示最近一次任务开始前", false)
                ),
                args -> restore(args.get("offset"))
        );
    }

    private String restore(String value) {
        SnapshotService snapshotService = snapshotServiceSupplier.get();
        if (snapshotService == null) {
            return "恢复快照失败: 快照服务未初始化";
        }
        try {
            RestoreResult result = snapshotService.restorePreTurn(Math.max(1, parseInt(value, 1)));
            return result.formatForCli();
        } catch (Exception e) {
            return "恢复快照失败: " + e.getMessage();
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
