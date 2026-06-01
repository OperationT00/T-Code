package com.tcode.tool;

import java.util.function.Function;

public final class ShellToolsProvider implements ToolProvider {
    private final Function<String, String> execute;

    public ShellToolsProvider(Function<String, String> execute) {
        this.execute = execute;
    }

    @Override
    public void register(ToolRegistrationContext context) {
        context.register(
                "execute_command",
                "在当前项目目录中执行短时 Shell 命令（默认 60 秒超时，不允许全盘扫描）",
                context.parameters(
                        context.param("command", "string", "要执行的命令", true)
                ),
                args -> execute.apply(args.get("command"))
        );
    }
}
