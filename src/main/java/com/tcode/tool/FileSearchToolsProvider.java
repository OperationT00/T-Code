package com.tcode.tool;

import java.util.Map;
import java.util.function.Function;

public final class FileSearchToolsProvider implements ToolProvider {
    private final Function<Map<String, String>, String> glob;
    private final Function<Map<String, String>, String> grep;

    public FileSearchToolsProvider(Function<Map<String, String>, String> glob,
                                   Function<Map<String, String>, String> grep) {
        this.glob = glob;
        this.grep = grep;
    }

    @Override
    public void register(ToolRegistrationContext context) {
        context.register(
                "glob_files",
                "按文件名 glob 查找项目内文件（只读、实时、尊重常见忽略目录）；适合先定位候选文件，例如 **/*Service.java",
                context.parameters(
                        context.param("pattern", "string", "glob 模式，例如 **/*.java、**/*Controller*、README.md", true),
                        context.param("path", "string", "搜索起始目录，默认 .", false),
                        context.param("max_results", "integer", "最多返回结果数，默认 50，上限 200", false)
                ),
                glob::apply
        );
        context.register(
                "grep_code",
                "在项目内按关键字或正则实时搜索代码（只读、返回文件和行号）；适合精确符号/字符串定位，找到后再 read_file 读取上下文",
                context.parameters(
                        context.param("pattern", "string", "要搜索的关键字或正则", true),
                        context.param("path", "string", "搜索起始目录，默认 .", false),
                        context.param("glob", "string", "可选文件 glob 过滤，例如 **/*.java", false),
                        context.param("regex", "boolean", "是否按 Java 正则解释 pattern，默认 false 表示字面量搜索", false),
                        context.param("case_sensitive", "boolean", "是否大小写敏感，默认 true", false),
                        context.param("context_lines", "integer", "每条命中前后上下文行数，默认 0，上限 5", false),
                        context.param("max_results", "integer", "最多返回命中数，默认 50，上限 200", false)
                ),
                grep::apply
        );
    }
}
