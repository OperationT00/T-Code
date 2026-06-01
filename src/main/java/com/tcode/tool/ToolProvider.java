package com.tcode.tool;

@FunctionalInterface
public interface ToolProvider {
    void register(ToolRegistrationContext context);
}
