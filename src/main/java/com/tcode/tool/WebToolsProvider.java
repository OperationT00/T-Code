package com.tcode.tool;

import java.util.function.BiFunction;

public final class WebToolsProvider implements ToolProvider {
    private static final int DEFAULT_FETCH_MAX_CHARS = 8000;

    private final BiFunction<String, Integer, String> search;
    private final BiFunction<String, Integer, String> fetch;

    public WebToolsProvider(BiFunction<String, Integer, String> search,
                            BiFunction<String, Integer, String> fetch) {
        this.search = search;
        this.fetch = fetch;
    }

    @Override
    public void register(ToolRegistrationContext context) {
        context.register(
                "web_search",
                "搜索互联网，获取实时信息（最新版本、官方文档、技术资讯等）。"
                        + "支持 SerpAPI（默认）和 SearXNG（自托管）两种 provider，由 SEARCH_PROVIDER 环境变量切换。",
                context.parameters(
                        context.param("query", "string", "搜索关键词，例如'Java 21 新特性'、'Spring Boot 3.3 release notes'", true),
                        context.param("top_k", "integer", "返回结果数量（默认5）", false)
                ),
                args -> search.apply(args.get("query"), parseInt(args.get("top_k"), 5))
        );
        context.register(
                "web_fetch",
                "抓取指定 URL，提取正文转 Markdown。"
                        + "适用静态 / SSR 页面（博客、文档、官网）；JS 渲染或防爬站会返回空正文，本期不重试。",
                context.parameters(
                        context.param("url", "string", "完整 URL，需 http 或 https 协议", true),
                        context.param("max_chars", "integer", "返回 Markdown 最大字符数（默认 8000，超出截断）", false)
                ),
                args -> fetch.apply(args.get("url"), parseInt(args.get("max_chars"), DEFAULT_FETCH_MAX_CHARS))
        );
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
