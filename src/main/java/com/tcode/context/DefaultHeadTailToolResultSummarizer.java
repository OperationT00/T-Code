package com.tcode.context;

public final class DefaultHeadTailToolResultSummarizer implements ToolResultSummarizer {
    @Override
    public boolean supports(String toolName) {
        return true;
    }

    @Override
    public String summarize(String toolName, String result, ToolSummaryPolicy policy) {
        ToolSummaryPolicy activePolicy = policy == null
                ? ToolSummaryPolicy.forLevel(ContextPressureLevel.NORMAL)
                : policy;
        if (result == null || result.length() <= activePolicy.maxChars()) {
            return result;
        }
        int edgeChars = Math.min(activePolicy.edgeChars(), Math.max(0, result.length()));
        int omitted = Math.max(0, result.length() - edgeChars * 2);
        return """
                [Tool result summarized: original %,d chars, omitted %,d chars]
                Tool: %s

                --- head ---
                %s

                --- tail ---
                %s
                """.formatted(
                result.length(),
                omitted,
                toolName == null || toolName.isBlank() ? "unknown" : toolName,
                result.substring(0, edgeChars),
                result.substring(Math.max(0, result.length() - edgeChars))
        ).trim();
    }
}
