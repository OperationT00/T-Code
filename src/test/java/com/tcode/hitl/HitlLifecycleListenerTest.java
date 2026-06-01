package com.tcode.hitl;

import com.tcode.tool.ToolOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitlLifecycleListenerTest {

    @Test
    void emitsRequestedAndResolvedAroundExplicitApproval(@TempDir Path tempDir) {
        List<String> events = new ArrayList<>();
        HitlToolRegistry registry = new HitlToolRegistry(new ApprovingHandler());
        registry.setProjectPath(tempDir.toString());
        registry.setHitlLifecycleListener(new HitlLifecycleListener() {
            @Override
            public void onRequested(ApprovalRequest request) {
                events.add("requested:" + request.toolName());
            }

            @Override
            public void onResolved(ApprovalRequest request, ApprovalResult result) {
                events.add("resolved:" + request.toolName() + ":" + result.decision());
            }
        });

        ToolOutput output = registry.executeToolOutput(
                "write_file", "{\"path\":\"demo.txt\",\"content\":\"ok\"}");

        assertTrue(output.text().contains("文件已写入"));
        assertEquals(List.of(
                "requested:write_file",
                "resolved:write_file:APPROVED"
        ), events);
    }

    private static final class ApprovingHandler implements HitlHandler {
        @Override
        public ApprovalResult requestApproval(ApprovalRequest request) {
            return ApprovalResult.approve();
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void setEnabled(boolean enabled) {
        }
    }
}
