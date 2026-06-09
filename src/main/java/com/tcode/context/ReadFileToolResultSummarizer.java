package com.tcode.context;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ReadFileToolResultSummarizer implements ToolResultSummarizer {
    private static final Pattern PATH_LINE = Pattern.compile(".*(?:/|\\\\).+\\.[A-Za-z0-9]+.*");
    private static final Pattern STRUCTURE_LINE = Pattern.compile(
            "^\\s*(package\\s+.+;|import\\s+.+;|(?:public|private|protected|final|abstract|static|sealed|non-sealed|record|class|interface|enum)\\b.*)$");

    @Override
    public boolean supports(String toolName) {
        return "read_file".equalsIgnoreCase(toolName);
    }

    @Override
    public String summarize(String toolName, String result, ToolSummaryPolicy policy) {
        ToolSummaryPolicy activePolicy = policy == null
                ? ToolSummaryPolicy.forLevel(ContextPressureLevel.NORMAL)
                : policy;
        if (result == null || result.length() <= activePolicy.maxChars()) {
            return result;
        }

        List<String> paths = new ArrayList<>();
        List<String> structure = new ArrayList<>();
        int maxStructureLines = Math.max(10, activePolicy.maxChars() / 120);
        for (String line : result.lines().toList()) {
            String trimmed = line.trim();
            if (paths.size() < 10 && PATH_LINE.matcher(trimmed).matches()) {
                paths.add(trimmed);
            }
            if (structure.size() < maxStructureLines && STRUCTURE_LINE.matcher(line).matches()) {
                structure.add(line.stripTrailing());
            }
        }

        int edgeChars = Math.min(activePolicy.edgeChars(), result.length());
        String head = compactRepeatedLines(result.substring(0, edgeChars), 2);
        String tail = compactRepeatedLines(result.substring(Math.max(0, result.length() - edgeChars)), 2);

        return """
                [read_file summarized]
                Original: %,d chars
                Tool: %s

                --- files ---
                %s

                --- head ---
                %s

                --- structure ---
                %s

                --- tail ---
                %s
                """.formatted(
                result.length(),
                toolName == null || toolName.isBlank() ? "read_file" : toolName,
                paths.isEmpty() ? "(unknown)" : String.join("\n", paths),
                head,
                structure.isEmpty() ? "(none)" : String.join("\n", structure),
                tail
        ).trim();
    }

    private static String compactRepeatedLines(String text, int maxRepeats) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        StringBuilder sb = new StringBuilder();
        String previous = null;
        int repeats = 0;
        for (String line : text.lines().toList()) {
            if (line.equals(previous)) {
                repeats++;
                if (repeats <= maxRepeats) {
                    sb.append(line).append('\n');
                }
            } else {
                previous = line;
                repeats = 1;
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }
}
