package com.tcode.tool;

import com.tcode.snapshot.RestoreResult;
import com.tcode.snapshot.SideGitManager;
import com.tcode.snapshot.SnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotToolsProviderTest {

    @Test
    void restoresSnapshotUsingNormalizedOffset(@TempDir Path tempDir) {
        AtomicInteger restoredOffset = new AtomicInteger();
        SnapshotService service = new SnapshotService(new SideGitManager(tempDir)) {
            @Override
            public RestoreResult restorePreTurn(int offset) {
                restoredOffset.set(offset);
                return RestoreResult.success("1234567890abcdef", List.of("README.md"), List.of());
            }
        };
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new SnapshotToolsProvider(() -> service));

        String result = registry.executeTool("revert_turn", "{\"offset\":\"0\"}");

        assertEquals(1, restoredOffset.get());
        assertTrue(result.contains("已恢复到快照"));
        service.close();
    }
}
