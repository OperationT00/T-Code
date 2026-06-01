package com.tcode.runtime;

import com.tcode.llm.LlmClient;
import com.tcode.hitl.ApprovalRequest;
import com.tcode.hitl.ApprovalResult;
import com.tcode.hitl.HitlHandler;
import com.tcode.runtime.api.RuntimeEventSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreRuntimeTest {

    @Test
    void headlessRunnerCreatesAgentForConfiguredProjectAndEmitsStatusEvents() throws Exception {
        Path projectDir = Path.of(".").toAbsolutePath().normalize();
        CoreRuntime runtime = CoreRuntime.headless(new ReplyClient(), projectDir);
        List<String> events = new ArrayList<>();
        RuntimeEventSink sink = (type, data) -> events.add(type + ":" + data);

        String result = runtime.turnRunner().run("hello", sink);

        assertEquals("reply:hello", result);
        assertEquals(projectDir.toString(), runtime.projectPath());
        assertTrue(events.stream().anyMatch(event -> event.contains(
                "status.updated:{\"schema_version\":1,\"phase\":\"running\"}")));
        assertTrue(events.stream().anyMatch(event -> event.contains(
                "status.updated:{\"schema_version\":1,\"phase\":\"idle\"}")));
    }

    @Test
    void headlessRunnerEmitsToolLifecycleEvents() throws Exception {
        CoreRuntime runtime = CoreRuntime.headless(new ToolCallingClient(), Path.of("."));
        List<String> events = new ArrayList<>();

        runtime.turnRunner().run("list files", (type, data) -> events.add(type + ":" + data));

        assertTrue(events.stream().anyMatch(event -> event.contains(
                "tool.started:{\"schema_version\":1,\"name\":\"list_dir\"")));
        assertTrue(events.stream().anyMatch(event -> event.contains(
                "tool.completed:{\"schema_version\":1,\"name\":\"list_dir\"")));
    }

    @Test
    void headlessRunnerEmitsHitlEventsWhenHandlerIsConfigured(@TempDir Path tempDir) throws Exception {
        CoreRuntime runtime = CoreRuntime.headless(
                new DangerousToolCallingClient(), tempDir, new ApprovingHitlHandler());
        List<String> events = new ArrayList<>();

        runtime.turnRunner().run("write file", (type, data) -> events.add(type + ":" + data));

        assertTrue(events.stream().anyMatch(event -> event.contains(
                "hitl.requested:{\"schema_version\":1,\"tool\":\"write_file\"")));
        assertTrue(events.stream().anyMatch(event -> event.contains(
                "hitl.resolved:{\"schema_version\":1,\"tool\":\"write_file\"")));
    }

    private static final class ReplyClient implements LlmClient {
        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            String latestUser = messages.stream()
                    .filter(message -> "user".equals(message.role()))
                    .reduce((first, second) -> second)
                    .map(Message::content)
                    .orElse("");
            return new ChatResponse("assistant", "reply:" + latestUser, List.of(), 1, 1);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            return chat(messages, tools);
        }

        @Override
        public String getModelName() {
            return "test-model";
        }

        @Override
        public String getProviderName() {
            return "test-provider";
        }
    }

    private static final class ToolCallingClient implements LlmClient {
        private final Queue<ChatResponse> responses = new ArrayDeque<>(List.of(
                new ChatResponse("assistant", "", List.of(
                        new ToolCall("call_1", new ToolCall.Function("list_dir", "{\"path\":\".\"}"))
                ), 1, 1),
                new ChatResponse("assistant", "done", List.of(), 1, 1)
        ));

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            return responses.remove();
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            return chat(messages, tools);
        }

        @Override
        public String getModelName() {
            return "test-model";
        }

        @Override
        public String getProviderName() {
            return "test-provider";
        }
    }

    private static final class DangerousToolCallingClient implements LlmClient {
        private final Queue<ChatResponse> responses = new ArrayDeque<>(List.of(
                new ChatResponse("assistant", "", List.of(
                        new ToolCall("call_1", new ToolCall.Function(
                                "write_file", "{\"path\":\"runtime-hitl-test.txt\",\"content\":\"ok\"}"))
                ), 1, 1),
                new ChatResponse("assistant", "done", List.of(), 1, 1)
        ));

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            return responses.remove();
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            return chat(messages, tools);
        }

        @Override
        public String getModelName() {
            return "test-model";
        }

        @Override
        public String getProviderName() {
            return "test-provider";
        }
    }

    private static final class ApprovingHitlHandler implements HitlHandler {
        @Override
        public ApprovalResult requestApproval(ApprovalRequest request) {
            return ApprovalResult.approve();
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void setEnabled(boolean enabled) {
        }
    }
}
