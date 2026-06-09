package com.tcode.cli;

import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainInputNormalizationTest {

    @Test
    void keepsMultilinePasteStructure() {
        String normalized = CliInputNormalization.prepareSeedBuffer("请把任务拆成可并行的 DAG:\n1. 读 pom.xml\r\n2. 列出 src/main/java");

        assertEquals("请把任务拆成可并行的 DAG:\n1. 读 pom.xml\n2. 列出 src/main/java", normalized);
    }

    @Test
    void keepsSingleLineInputUntouched() {
        String normalized = CliInputNormalization.prepareSeedBuffer("帮我读取 pom.xml");

        assertEquals("帮我读取 pom.xml", normalized);
    }

    @Test
    void normalizesLegacyCarriageReturnsWithoutChangingTextLayout() {
        String normalized = CliInputNormalization.prepareSeedBuffer("第一行\r第二行\r\n第三行");

        assertEquals("第一行\n第二行\n第三行", normalized);
    }

    @Test
    void startupHintsKeepSlashCommandDetailsOutOfInitialScreen() {
        List<String> hints = CliPresentation.startupHints();

        assertTrue(hints.stream().anyMatch(hint -> hint.contains("输入 '/' 后按 Tab 补全命令")));
        assertTrue(hints.stream().noneMatch(hint -> hint.contains("/model")));
        assertTrue(hints.stream().noneMatch(hint -> hint.contains("/index [路径]")));
        assertTrue(hints.stream().noneMatch(hint -> hint.contains("/skill list")));
    }

    @Test
    void startupBannerUsesOpenLayoutWithoutRightBorder() {
        List<String> lines = CliPresentation.startupBannerLines();

        assertTrue(lines.stream().anyMatch(line -> line.contains("t-code")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("v1.0.2")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("██████████")));
        assertTrue(lines.stream().filter(line -> line.contains("██")).count() >= 5,
                "banner should render a five-line uppercase T logo");
        assertTrue(lines.stream().noneMatch(line -> line.contains("π")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("Browser · Image")),
                "banner capability summary should keep image input out of the headline");
        assertTrue(lines.stream().anyMatch(line -> line.contains("Tips for getting started")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("@path")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("for shortcuts")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("────────────────")));
        assertTrue(lines.stream().noneMatch(line -> line.endsWith("║")),
                "banner should not depend on a padded right border");
    }

    @Test
    void slashCommandTailTipsExposeCommandDescriptions() {
        var tips = CliInputWidgets.slashCommandTailTips();

        assertTrue(tips.containsKey("/model"));
        assertTrue(tips.get("/model").getMainDesc().get(0).toString().contains("查看当前模型"));
        assertTrue(tips.containsKey("/plan <任务内容>"));
    }

    @Test
    void promptDoesNotUseBottomSpaciousModeByDefault() {
        assertFalse(CliPromptInput.defaultSpaciousPrompt(false));
        assertFalse(CliPromptInput.defaultSpaciousPrompt(true));
    }

    @Test
    void mcpStartupWaitCanBeTunedForTerminalSmoke() {
        String old = System.getProperty("tcode.mcp.startup.wait.seconds");
        try {
            System.setProperty("tcode.mcp.startup.wait.seconds", "2");

            assertEquals(Duration.ofSeconds(2), CliStartupStatus.mcpStartupWait());
        } finally {
            restoreProperty("tcode.mcp.startup.wait.seconds", old);
        }
    }

    @Test
    void submittedPromptIsRenderedBackIntoTranscript() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        CliSubmittedInput.printPrompt(new PrintStream(sink, true, StandardCharsets.UTF_8), "  沉默王二是谁？  ");

        String emitted = sink.toString(StandardCharsets.UTF_8);
        assertTrue(emitted.contains(">"), emitted);
        assertTrue(emitted.contains("沉默王二是谁？"), emitted);
        assertTrue(emitted.endsWith("\n"), emitted);
        assertFalse(emitted.endsWith("\n\n"), emitted);
    }

    @Test
    void submittedSingleLinePromptDoesNotAddExtraBlankLine() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        CliSubmittedInput.printPrompt(new PrintStream(sink, true, StandardCharsets.UTF_8), "你好啊");

        String emitted = sink.toString(StandardCharsets.UTF_8);
        assertEquals(1, emitted.chars().filter(ch -> ch == '\n').count(), emitted);
        assertTrue(emitted.contains(">"), emitted);
        assertTrue(emitted.contains("你好啊"), emitted);
    }

    @Test
    void submittedSlashCommandIsRenderedBackIntoTranscript() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        CliSubmittedInput.print(null, new PrintStream(sink, true, StandardCharsets.UTF_8), "/memory list");

        String emitted = sink.toString(StandardCharsets.UTF_8);
        assertTrue(emitted.contains(">"), emitted);
        assertTrue(emitted.contains("/memory list"), emitted);
        assertEquals(1, emitted.chars().filter(ch -> ch == '\n').count(), emitted);
    }

    @Test
    void configuresAwtHeadlessOnMac() {
        String oldOs = System.getProperty("os.name");
        String oldHeadless = System.getProperty("java.awt.headless");
        try {
            System.setProperty("os.name", "Mac OS X");
            System.clearProperty("java.awt.headless");

            CliEnvironmentConfig.configureAwtForCli();

            assertEquals("true", System.getProperty("java.awt.headless"));
        } finally {
            restoreProperty("os.name", oldOs);
            restoreProperty("java.awt.headless", oldHeadless);
        }
    }

    @Test
    void doesNotForceAwtHeadlessOnNonMac() {
        String oldOs = System.getProperty("os.name");
        String oldHeadless = System.getProperty("java.awt.headless");
        try {
            System.setProperty("os.name", "Linux");
            System.clearProperty("java.awt.headless");

            CliEnvironmentConfig.configureAwtForCli();

            assertFalse(System.getProperties().containsKey("java.awt.headless"));
        } finally {
            restoreProperty("os.name", oldOs);
            restoreProperty("java.awt.headless", oldHeadless);
        }
    }

    @Test
    void clearsCurrentInputBufferForEscWidget() throws Exception {
        LineReader lineReader = newLineReader();
        lineReader.getBuffer().write("@image:</tmp/shot.png> 这张图呢");

        CliInputWidgets.clearInputBuffer(lineReader);

        assertEquals("", lineReader.getBuffer().toString());
    }

    @Test
    void slashCommandHintsDoNotIncludeRagSlashCommands() {
        List<String> commands = CliPresentation.slashCommandHints().stream()
                .map(CliPresentation.SlashCommandHint::display)
                .toList();

        assertFalse(commands.stream().anyMatch(command -> command.startsWith("/index")));
        assertFalse(commands.stream().anyMatch(command -> command.startsWith("/search")));
        assertFalse(commands.stream().anyMatch(command -> command.startsWith("/graph")));
    }

    @Test
    void slashCommandChoicesAreRenderedDirectlyWithoutJLineConfirmationText() {
        String choices = CliPresentation.formatSlashCommandChoices(120);

        assertTrue(choices.contains("/model glm-5.1"), choices);
        assertTrue(choices.contains("/model glm-5v-turbo"), choices);
        assertTrue(choices.contains("/model step"), choices);
        assertTrue(choices.contains("/model kimi"), choices);
        assertTrue(choices.contains("/browser status"), choices);
        assertFalse(choices.contains("do you wish"), choices);
        assertTrue(choices.lines().count() < CliPresentation.slashCommandHints().size(),
                "choices should be compact multi-column output");
    }

    @Test
    void classifiesStandaloneEscapeAsCancelIntent() {
        assertEquals(CliInputNormalization.EscapeSequenceType.STANDALONE_ESC,
                CliInputNormalization.classifyEscapeSequence(""));
    }

    @Test
    void classifiesArrowKeysAsControlSequences() {
        assertEquals(CliInputNormalization.EscapeSequenceType.CONTROL_SEQUENCE,
                CliInputNormalization.classifyEscapeSequence("[A"));
        assertEquals(CliInputNormalization.EscapeSequenceType.CONTROL_SEQUENCE,
                CliInputNormalization.classifyEscapeSequence("[B"));
        assertEquals(CliInputNormalization.EscapeSequenceType.CONTROL_SEQUENCE,
                CliInputNormalization.classifyEscapeSequence("OA"));
    }

    @Test
    void classifiesBracketedPasteSequenceSeparately() {
        assertEquals(CliInputNormalization.EscapeSequenceType.BRACKETED_PASTE,
                CliInputNormalization.classifyEscapeSequence("[200~hello"));
    }

    @Test
    void upArrowPrefillsLatestHistoryEntry() throws Exception {
        LineReader lineReader = newLineReader();
        History history = lineReader.getHistory();
        history.add("第一条");
        history.add("最近一条");

        assertEquals("最近一条", CliInputHistory.seedBufferForHistoryNavigation(lineReader, "[A"));
    }

    @Test
    void downArrowKeepsPromptEmpty() throws Exception {
        LineReader lineReader = newLineReader();
        lineReader.getHistory().add("最近一条");

        assertEquals("", CliInputHistory.seedBufferForHistoryNavigation(lineReader, "[B"));
    }

    @Test
    void decideEscCancelTriggersOnStandaloneEsc() {
        // 单 ESC（escTail 为空）→ 取消
        assertTrue(CliTerminalInput.decideEscCancel(27, ""));
        assertTrue(CliTerminalInput.decideEscCancel(27, null));
    }

    @Test
    void decideEscCancelIgnoresArrowKeyEscapeSequence() {
        // 上方向键 ESC[A → CONTROL_SEQUENCE，不取消
        assertFalse(CliTerminalInput.decideEscCancel(27, "[A"));
        // 下方向键
        assertFalse(CliTerminalInput.decideEscCancel(27, "[B"));
        // 应用模式方向键
        assertFalse(CliTerminalInput.decideEscCancel(27, "OA"));
    }

    @Test
    void decideEscCancelIgnoresBracketedPaste() {
        assertFalse(CliTerminalInput.decideEscCancel(27, "[200~hello"));
    }

    @Test
    void decideEscCancelIgnoresNonEscFirstByte() {
        // 普通字符不应触发
        assertFalse(CliTerminalInput.decideEscCancel((int) 'a', null));
        assertFalse(CliTerminalInput.decideEscCancel((int) '/', "cancel"));
        assertFalse(CliTerminalInput.decideEscCancel(0, null));
        assertFalse(CliTerminalInput.decideEscCancel(-1, null));
    }

    @Test
    void readEscCancelHandlesNullTerminalSafely() {
        assertFalse(CliTerminalInput.readEscCancel(null));
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static LineReader newLineReader() throws Exception {
        Terminal terminal = TerminalBuilder.builder()
                .dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .build();

        DefaultHistory history = new DefaultHistory();
        return LineReaderBuilder.builder()
                .terminal(terminal)
                .history(history)
                .build();
    }
}
