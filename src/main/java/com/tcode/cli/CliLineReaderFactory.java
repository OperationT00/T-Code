package com.tcode.cli;

import com.tcode.mcp.resources.McpResourceDescriptor;
import com.tcode.skill.Skill;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

final class CliLineReaderFactory {

    private CliLineReaderFactory() {
    }

    static LineReader create(Terminal terminal,
                             Supplier<List<McpResourceDescriptor>> resourceCandidates,
                             Supplier<List<Skill>> skills,
                             Path home) {
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .history(new TCodeHistory())
                .completer(new TCodeCompleter(resourceCandidates, skills))
                .highlighter(new TCodeHighlighter())
                .build();
        lineReader.option(LineReader.Option.BRACKETED_PASTE, true);
        lineReader.option(LineReader.Option.AUTO_LIST, true);
        lineReader.option(LineReader.Option.AUTO_MENU, true);
        CliInputHistory.configureHistory(lineReader, home);
        CliInputWidgets.configureSlashCommandHint(lineReader);
        CliInputWidgets.configureJLineInteractiveWidgets(lineReader);
        return lineReader;
    }
}
