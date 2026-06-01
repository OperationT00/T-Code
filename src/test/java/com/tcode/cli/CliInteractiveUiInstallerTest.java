package com.tcode.cli;

import com.tcode.hitl.ApprovalRequest;
import com.tcode.hitl.ApprovalResult;
import com.tcode.llm.LlmClient;
import com.tcode.render.Renderer;
import com.tcode.render.StatusInfo;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliInteractiveUiInstallerTest {

    @Test
    void printsPlainStartupScreenAndInstallsRuntimeWidgets() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        StubRenderer renderer = new StubRenderer(new PrintStream(sink, true, StandardCharsets.UTF_8));
        try (Terminal terminal = TerminalBuilder.builder()
                .dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .build()) {
            LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).build();

            CliInteractiveUiInstaller.installStartupScreen(renderer, renderer.stream(), List.of("one", "two"));
            boolean spaciousPrompt = CliInteractiveUiInstaller.installRuntimeWidgets(renderer, lineReader);

            String output = sink.toString(StandardCharsets.UTF_8);
            assertTrue(output.contains("one"));
            assertTrue(output.contains("two"));
            assertFalse(spaciousPrompt);
            assertTrue(lineReader.getWidgets().containsKey("tcode-paste-clipboard-image"));
            assertTrue(lineReader.getWidgets().containsKey("tcode-clear-input"));
        }
    }

    private record StubRenderer(PrintStream stream) implements Renderer {
        @Override public void start() {}
        @Override public void close() {}
        @Override public void appendToolCalls(List<LlmClient.ToolCall> toolCalls) {}
        @Override public void appendDiff(String filePath, String before, String after) {}
        @Override public void updateStatus(StatusInfo status) {}
        @Override public ApprovalResult promptApproval(ApprovalRequest request) { return ApprovalResult.approve(); }
        @Override public int openPalette(String title, List<String> items) { return -1; }
    }
}
