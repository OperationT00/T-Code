package com.tcode.tool;

import java.util.Map;
import java.util.function.Function;

public final class FileToolsProvider implements ToolProvider {
    private final Function<Map<String, String>, String> read;
    private final Function<Map<String, String>, String> write;
    private final Function<Map<String, String>, String> list;

    public FileToolsProvider(Function<Map<String, String>, String> read,
                             Function<Map<String, String>, String> write,
                             Function<Map<String, String>, String> list) {
        this.read = read;
        this.write = write;
        this.list = list;
    }

    @Override
    public void register(ToolRegistrationContext context) {
        context.register(
                "read_file",
                "读取文件内容（仅限项目根目录之内）；可用 offset/limit 按行读取，避免把大文件整段塞进上下文",
                context.parameters(
                        context.param("path", "string", "文件路径", true),
                        context.param("offset", "integer", "起始行号，1 表示第一行；省略时读取全文", false),
                        context.param("limit", "integer", "最多读取多少行；省略时读取全文，最大 2000 行", false)
                ),
                read::apply
        );
        context.register(
                "write_file",
                "写入文件内容（仅限项目根目录之内，单文件 5MB 上限）",
                context.parameters(
                        context.param("path", "string", "文件路径", true),
                        context.param("content", "string", "文件内容", true)
                ),
                write::apply
        );
        context.register(
                "list_dir",
                "列出目录内容（仅限项目根目录之内）",
                context.parameters(context.param("path", "string", "目录路径", true)),
                list::apply
        );
    }
}
