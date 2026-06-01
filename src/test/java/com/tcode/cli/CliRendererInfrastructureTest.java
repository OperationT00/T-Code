package com.tcode.cli;

import com.tcode.hitl.ApprovalRequest;
import com.tcode.hitl.ApprovalResult;
import com.tcode.hitl.RendererHitlHandler;
import com.tcode.hitl.SwitchableHitlHandler;
import com.tcode.hitl.TerminalHitlHandler;
import com.tcode.llm.LlmClient;
import com.tcode.render.Renderer;
import com.tcode.render.StatusInfo;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliRendererInfrastructureTest {

    @Test
    void startsRendererUpdatesStatusAndInstallsHitlDelegate() {
        StubRenderer renderer = new StubRenderer();
        SwitchableHitlHandler hitlHandler = new SwitchableHitlHandler(new TerminalHitlHandler(false));
        StatusInfo initialStatus = StatusInfo.idle("glm-5.1", 200_000L, false);

        CliRendererInfrastructure infrastructure =
                CliRendererInfrastructure.start(renderer, null, hitlHandler, initialStatus);

        assertSame(renderer, infrastructure.renderer());
        assertSame(renderer.stream(), infrastructure.ui());
        assertTrue(renderer.started);
        assertSame(initialStatus, renderer.status);
        assertTrue(hitlHandler.getDelegate() instanceof RendererHitlHandler);
    }

    private static final class StubRenderer implements Renderer {
        private boolean started;
        private StatusInfo status;

        @Override public void start() { started = true; }
        @Override public void close() {}
        @Override public PrintStream stream() { return System.out; }
        @Override public void appendToolCalls(List<LlmClient.ToolCall> toolCalls) {}
        @Override public void appendDiff(String filePath, String before, String after) {}
        @Override public void updateStatus(StatusInfo status) { this.status = status; }
        @Override public ApprovalResult promptApproval(ApprovalRequest request) { return ApprovalResult.approve(); }
        @Override public int openPalette(String title, List<String> items) { return -1; }
    }
}
