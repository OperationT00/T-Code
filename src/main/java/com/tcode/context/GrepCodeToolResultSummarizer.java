package com.tcode.context;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class GrepCodeToolResultSummarizer implements ToolResultSummarizer {
    private static final Pattern PATH_LINE_PATTERN = Pattern.compile("^.+?:\\d+:.*$");

    @Override
    public boolean supports(String toolName) {
        return "grep_code".equalsIgnoreCase(toolName);
    }

    @Override
    public String summarize(String toolName, String result, ToolSummaryPolicy policy) {
        ToolSummaryPolicy activePolicy = policy == null
                ? ToolSummaryPolicy.forLevel(ContextPressureLevel.NORMAL)
                : policy;
        if (result == null || result.length() <= activePolicy.maxChars()) {
            return result;
        }

        List<String> matches = new ArrayList<>();
        int totalMatches = 0;
        int maxLines = Math.max(5, activePolicy.maxChars() / 120);
        for (String line : result.lines().toList()) {
            if (PATH_LINE_PATTERN.matcher(line).matches()) {
                totalMatches++;
                if (matches.size() < maxLines) {
                    matches.add(line);
                }
            }
        }

        if (matches.isEmpty()) {
            return new DefaultHeadTailToolResultSummarizer().summarize(toolName, result, activePolicy);
        }

        return """
                [grep_code summarized]
                Original: %,d chars
                Matches: %,d, shown: %,d
                Tool: %s

                --- matches ---
                %s
                """.formatted(
                result.length(),
                totalMatches,
                matches.size(),
                toolName == null || toolName.isBlank() ? "grep_code" : toolName,
                String.join("\n", matches)
        ).trim();
    }
}
