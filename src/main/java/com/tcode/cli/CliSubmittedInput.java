package com.tcode.cli;

import com.tcode.render.Renderer;
import com.tcode.render.inline.InlineRenderer;
import com.tcode.util.AnsiStyle;

import java.io.PrintStream;

final class CliSubmittedInput {
    private CliSubmittedInput() {
    }

    static void print(Renderer renderer, PrintStream out, String input) {
        if (renderer instanceof InlineRenderer inline) {
            inline.printSubmittedPrompt(input);
        } else {
            printPrompt(out, input);
        }
    }

    static void printPrompt(PrintStream out, String input) {
        String visible = input == null ? "" : input.strip();
        if (visible.isEmpty()) {
            return;
        }
        out.println(AnsiStyle.userMessageBlock(visible, terminalColumns()));
    }

    private static int terminalColumns() {
        String columns = System.getenv("COLUMNS");
        if (columns != null && !columns.isBlank()) {
            try {
                return Math.max(40, Integer.parseInt(columns.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return 120;
    }
}
