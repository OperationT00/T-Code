package com.tcode.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolLifecycleListenerTest {

    @Test
    void emitsStartedAndCompletedAroundToolOutputExecution() {
        List<String> events = new ArrayList<>();
        ToolRegistry registry = new ToolRegistry();
        registry.setToolLifecycleListener(new ToolLifecycleListener() {
            @Override
            public void onStarted(String name, String argumentsJson) {
                events.add("started:" + name);
            }

            @Override
            public void onCompleted(String name, String argumentsJson, ToolOutput output) {
                events.add("completed:" + name + ":" + output.text());
            }
        });

        registry.executeToolOutput("list_dir", "{\"path\":\".\"}");

        assertEquals(2, events.size());
        assertEquals("started:list_dir", events.get(0));
        assertEquals(true, events.get(1).startsWith("completed:list_dir:目录内容:"));
    }

    @Test
    void listenerFailureDoesNotBreakToolExecution() {
        ToolRegistry registry = new ToolRegistry();
        registry.setToolLifecycleListener(new ToolLifecycleListener() {
            @Override
            public void onStarted(String name, String argumentsJson) {
                throw new IllegalStateException("listener down");
            }

            @Override
            public void onCompleted(String name, String argumentsJson, ToolOutput output) {
                throw new IllegalStateException("listener down");
            }
        });

        String result = registry.executeToolOutput("list_dir", "{\"path\":\".\"}").text();

        assertEquals(true, result.startsWith("目录内容:"));
    }
}
