package com.tcode.context;

import java.util.List;

public final class ToolResultSummarizers {
    private final List<ToolResultSummarizer> summarizers;

    public ToolResultSummarizers(List<ToolResultSummarizer> summarizers) {
        this.summarizers = summarizers == null || summarizers.isEmpty()
                ? List.of(new DefaultHeadTailToolResultSummarizer())
                : List.copyOf(summarizers);
    }

    public static ToolResultSummarizers defaults() {
        return new ToolResultSummarizers(List.of(
                new ExecuteCommandToolResultSummarizer(),
                new GrepCodeToolResultSummarizer(),
                new ReadFileToolResultSummarizer(),
                new DefaultHeadTailToolResultSummarizer()
        ));
    }

    public String summarize(String toolName, String result, ToolSummaryPolicy policy) {
        for (ToolResultSummarizer summarizer : summarizers) {
            if (summarizer.supports(toolName)) {
                return summarizer.summarize(toolName, result, policy);
            }
        }
        return new DefaultHeadTailToolResultSummarizer().summarize(toolName, result, policy);
    }
}
