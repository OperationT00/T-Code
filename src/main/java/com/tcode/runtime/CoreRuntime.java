package com.tcode.runtime;

import com.tcode.agent.Agent;
import com.tcode.hitl.ApprovalRequest;
import com.tcode.hitl.ApprovalResult;
import com.tcode.hitl.HitlHandler;
import com.tcode.hitl.HitlLifecycleListener;
import com.tcode.hitl.HitlToolRegistry;
import com.tcode.llm.LlmClient;
import com.tcode.runtime.api.RuntimeEventPayloads;
import com.tcode.runtime.api.RuntimeEventSink;
import com.tcode.runtime.api.RuntimeTurnRunner;
import com.tcode.tool.ToolRegistry;
import com.tcode.tool.ToolLifecycleListener;
import com.tcode.tool.ToolOutput;

import java.nio.file.Path;

public final class CoreRuntime {
    private final LlmClient llmClient;
    private final Path projectDir;
    private final HitlHandler hitlHandler;

    private CoreRuntime(LlmClient llmClient, Path projectDir, HitlHandler hitlHandler) {
        this.llmClient = llmClient;
        this.projectDir = projectDir == null ? Path.of(".").toAbsolutePath().normalize() : projectDir.toAbsolutePath().normalize();
        this.hitlHandler = hitlHandler;
    }

    public static CoreRuntime headless(LlmClient llmClient, Path projectDir) {
        return new CoreRuntime(llmClient, projectDir, null);
    }

    public static CoreRuntime headless(LlmClient llmClient, Path projectDir, HitlHandler hitlHandler) {
        return new CoreRuntime(llmClient, projectDir, hitlHandler);
    }

    public String projectPath() {
        return projectDir.toString();
    }

    public RuntimeTurnRunner turnRunner() {
        return (input, events) -> runHeadlessTurn(input, events);
    }

    public Agent createAgent() {
        return createAgent(null);
    }

    private Agent createAgent(RuntimeEventSink events) {
        ToolRegistry registry = hitlHandler == null ? new ToolRegistry() : new HitlToolRegistry(hitlHandler);
        registry.setProjectPath(projectPath());
        if (events != null) {
            registry.setToolLifecycleListener(new ToolLifecycleListener() {
                @Override
                public void onStarted(String name, String argumentsJson) {
                    events.emit("tool.started", RuntimeEventPayloads.toolStarted(name, argumentsJson));
                }

                @Override
                public void onCompleted(String name, String argumentsJson, ToolOutput output) {
                    events.emit("tool.completed",
                            RuntimeEventPayloads.toolCompleted(name, output == null ? "" : output.text()));
                }
            });
            if (registry instanceof HitlToolRegistry hitlRegistry) {
                hitlRegistry.setHitlLifecycleListener(new HitlLifecycleListener() {
                    @Override
                    public void onRequested(ApprovalRequest request) {
                        events.emit("hitl.requested",
                                RuntimeEventPayloads.hitlRequested(request.toolName(), request.arguments()));
                    }

                    @Override
                    public void onResolved(ApprovalRequest request, ApprovalResult result) {
                        events.emit("hitl.resolved",
                                RuntimeEventPayloads.hitlResolved(request.toolName(), result.decision().name()));
                    }
                });
            }
        }
        return new Agent(llmClient, registry);
    }

    private String runHeadlessTurn(String input, RuntimeEventSink events) throws Exception {
        RuntimeEventSink sink = events == null ? (type, data) -> {} : events;
        sink.emit("status.updated", RuntimeEventPayloads.statusUpdated("running"));
        try {
            return createAgent(sink).run(input);
        } finally {
            sink.emit("status.updated", RuntimeEventPayloads.statusUpdated("idle"));
        }
    }
}
