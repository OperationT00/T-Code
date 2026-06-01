package com.tcode.cli;

import com.tcode.hitl.ApprovalRequest;
import com.tcode.hitl.ApprovalResult;
import com.tcode.hitl.SwitchableHitlHandler;
import com.tcode.hitl.TerminalHitlHandler;
import com.tcode.llm.LlmClient;
import com.tcode.render.Renderer;
import com.tcode.render.StatusInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CliConfigCommandDispatcherTest {

    @Test
    void handlesClosedConfigPalette() {
        StubRenderer renderer = new StubRenderer();
        CliConfigCommandDispatcher.Context context = new CliConfigCommandDispatcher.Context(
                renderer, null, null,
                new SwitchableHitlHandler(new TerminalHitlHandler(false)), null);

        assertTrue(CliConfigCommandDispatcher.dispatch(
                new CliCommandParser.ParsedCommand(CliCommandParser.CommandType.CONFIG, null), context));
        assertTrue(renderer.output.toString().contains("已关闭"));
    }

    private static final class StubRenderer implements Renderer {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final PrintStream stream = new PrintStream(output);

        @Override public void start() {}
        @Override public void close() {}
        @Override public PrintStream stream() { return stream; }
        @Override public void appendToolCalls(List<LlmClient.ToolCall> toolCalls) {}
        @Override public void appendDiff(String filePath, String before, String after) {}
        @Override public void updateStatus(StatusInfo status) {}
        @Override public ApprovalResult promptApproval(ApprovalRequest request) { return ApprovalResult.approve(); }
        @Override public int openPalette(String title, List<String> items) { return -1; }
    }
}
