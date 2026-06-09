package com.tcode.memory;

public enum MemoryScope {
    PROJECT,
    GLOBAL;

    public static MemoryScope from(String scope) {
        return "global".equalsIgnoreCase(scope) ? GLOBAL : PROJECT;
    }

    public String label() {
        return this == GLOBAL ? "global" : "project";
    }
}
