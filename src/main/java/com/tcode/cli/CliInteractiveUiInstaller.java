package com.tcode.cli;

import com.tcode.render.Renderer;
import com.tcode.render.inline.InlineRenderer;
import org.jline.reader.LineReader;

import java.io.PrintStream;
import java.util.List;

final class CliInteractiveUiInstaller {

    private CliInteractiveUiInstaller() {
    }

    static void installStartupScreen(Renderer renderer, PrintStream ui, List<String> lines) {
        if (renderer instanceof InlineRenderer inline) {
            inline.installStartupScreen(lines);
            return;
        }
        for (String line : lines) {
            ui.println(line);
        }
    }

    static boolean installRuntimeWidgets(Renderer renderer, LineReader lineReader) {
        if (renderer instanceof InlineRenderer inline) {
            CliInputWidgets.bindCtrlOToFoldableBlocks(lineReader, inline);
        }
        CliInputWidgets.bindCtrlVToClipboardImage(lineReader);
        CliInputWidgets.bindEscToClearInput(lineReader);
        return CliPromptInput.defaultSpaciousPrompt(false);
    }
}
