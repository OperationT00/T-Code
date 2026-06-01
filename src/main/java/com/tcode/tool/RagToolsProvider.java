package com.tcode.tool;

import java.util.function.BiFunction;

public final class RagToolsProvider implements ToolProvider {
    private final BiFunction<String, Integer, String> search;

    public RagToolsProvider(BiFunction<String, Integer, String> search) {
        this.search = search;
    }

    @Override
    public void register(ToolRegistrationContext context) {
        context.register(
                "search_code",
                "RAG 语义辅助检索代码库，根据自然语言描述查找相关代码块；精确符号/字符串定位请优先用 grep_code/glob_files/read_file；默认 top_k=5，可显式指定（上限 30）",
                context.parameters(
                        context.param("query", "string", "自然语言查询描述，例如'用户登录的实现'", true),
                        context.param("top_k", "integer", "返回结果数量（默认 5，上限 30）", false)
                ),
                args -> search.apply(args.get("query"), parseInt(args.get("top_k"), 5))
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
