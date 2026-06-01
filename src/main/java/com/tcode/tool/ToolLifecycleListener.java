package com.tcode.tool;

public interface ToolLifecycleListener {
    ToolLifecycleListener NO_OP = new ToolLifecycleListener() {
        @Override
        public void onStarted(String name, String argumentsJson) {
        }

        @Override
        public void onCompleted(String name, String argumentsJson, ToolOutput output) {
        }
    };

    void onStarted(String name, String argumentsJson);

    void onCompleted(String name, String argumentsJson, ToolOutput output);
}
