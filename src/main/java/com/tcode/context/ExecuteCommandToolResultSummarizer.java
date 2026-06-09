package com.tcode.context;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ExecuteCommandToolResultSummarizer implements ToolResultSummarizer {
    private static final String[] IMPORTANT_MARKERS = {
            "exitCode",
            "exit code",
            "command:",
            "FAIL",
            "ERROR",
            "Exception",
            "Caused by",
            "expected",
            "actual",
            "BUILD FAILURE",
            "BUILD SUCCESS"
    };

    @Override
    public boolean supports(String toolName) {
        return "execute_command".equalsIgnoreCase(toolName);
    }

    @Override
    public String summarize(String toolName, String result, ToolSummaryPolicy policy) {
        ToolSummaryPolicy activePolicy = policy == null
                ? ToolSummaryPolicy.forLevel(ContextPressureLevel.NORMAL)
                : policy;
        if (result == null || result.length() <= activePolicy.maxChars()) {
            return result;
        }

        Set<String> importantLines = new LinkedHashSet<>();
        for (String line : result.lines().toList()) {
            if (isImportant(line)) {
                importantLines.add(line);
            }
            if (importantLines.size() >= 30) {
                break;
            }
        }

        int edgeChars = Math.min(activePolicy.edgeChars(), result.length());
        int omitted = Math.max(0, result.length() - edgeChars - importantLines.stream().mapToInt(String::length).sum());
        String tail = compactRepeatedLines(result.substring(Math.max(0, result.length() - edgeChars)), 3);

        return """
                [execute_command summarized]
                Original: %,d chars, omitted about %,d chars
                Tool: %s

                --- important lines ---
                %s

                --- tail ---
                %s
                """.formatted(
                result.length(),
                omitted,
                toolName == null || toolName.isBlank() ? "execute_command" : toolName,
                importantLines.isEmpty() ? "(none)" : String.join("\n", importantLines),
                tail
        ).trim();
    }

    private static boolean isImportant(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        for (String marker : IMPORTANT_MARKERS) {
            if (lower.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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
