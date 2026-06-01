package com.tcode.tool;

import java.util.Map;
import java.util.function.Function;

public final class ProjectToolsProvider implements ToolProvider {
    private final Function<Map<String, String>, String> create;

    public ProjectToolsProvider(Function<Map<String, String>, String> create) {
        this.create = create;
    }

    @Override
    public void register(ToolRegistrationContext context) {
        context.register(
                "create_project",
                "创建新项目结构",
                context.parameters(
                        context.param("name", "string", "项目名称", true),
                        context.param("type", "string", "项目类型 (java/python/node)", true)
                ),
                create::apply
        );
    }
}
