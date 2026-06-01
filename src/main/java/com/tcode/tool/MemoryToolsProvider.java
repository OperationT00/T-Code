package com.tcode.tool;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class MemoryToolsProvider implements ToolProvider {
    private final Supplier<BiConsumer<String, String>> saverSupplier;

    public MemoryToolsProvider(Supplier<BiConsumer<String, String>> saverSupplier) {
        this.saverSupplier = saverSupplier == null ? () -> null : saverSupplier;
    }

    @Override
    public void register(ToolRegistrationContext context) {
        context.register(
                "save_memory",
                "Save a stable cross-session fact only when the user explicitly asks to remember it.",
                context.parameters(
                        context.param("fact", "string", "Stable fact or preference to remember.", true),
                        context.param("scope", "string", "Memory scope: project or global. Defaults to project.", false)
                ),
                args -> {
                    String fact = args.get("fact");
                    if (fact == null || fact.isBlank()) {
                        return "保存长期记忆失败: fact 不能为空";
                    }
                    BiConsumer<String, String> saver = saverSupplier.get();
                    if (saver == null) {
                        return "保存长期记忆失败: 记忆保存器未初始化";
                    }
                    String normalized = fact.trim();
                    String scope = "global".equalsIgnoreCase(args.get("scope")) ? "global" : "project";
                    saver.accept(normalized, scope);
                    return "💾 已保存到长期记忆(" + scope + "): " + normalized;
                }
        );
    }
}
