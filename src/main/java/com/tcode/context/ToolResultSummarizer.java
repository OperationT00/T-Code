package com.tcode.context;

public interface ToolResultSummarizer {
    boolean supports(String toolName);

    String summarize(String toolName, String result, ToolSummaryPolicy policy);
}
