package com.tcode.agent;

import com.tcode.llm.GLMClient;
import com.tcode.llm.LlmClient;
import com.tcode.memory.MemoryManager;
import com.tcode.plan.ExecutionPlan;
import com.tcode.plan.Planner;
import com.tcode.plan.Task;
import com.tcode.tool.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanExecuteAgentTest {

    @TempDir
    Path tempDir;
    private String oldMemoryDir;

    @BeforeEach
    void isolateMemoryDir() {
        oldMemoryDir = System.getProperty("tcode.memory.dir");
        System.setProperty("tcode.memory.dir", tempDir.resolve("global-memory").toString());
    }

    @AfterEach
    void restoreMemoryDir() {
        if (oldMemoryDir == null) {
            System.clearProperty("tcode.memory.dir");
        } else {
            System.setProperty("tcode.memory.dir", oldMemoryDir);
        }
    }

    @Test
    void shouldNotWritePlanExecutionArtifactsToLongTermMemory() throws Exception {
        Path sampleFile = Files.createFile(tempDir.resolve("sample.txt"));
        Files.writeString(sampleFile, "plan-memory-content");

        StubGLMClient llmClient = new StubGLMClient(List.of(
                new LlmClient.ChatResponse(
                        "assistant",
                        "",
                        List.of(new LlmClient.ToolCall(
                                "call_1",
                                new LlmClient.ToolCall.Function(
                                        "read_file",
                                        "{\"path\":\"" + sampleFile.toString().replace("\\", "\\\\") + "\"}"
                                )
                        )),
                        120,
                        30
                ),
                new LlmClient.ChatResponse("assistant", "已读取并确认文件内容", null, 140, 40)
        ));

        MemoryManager memoryManager = new MemoryManager(
                llmClient,
                4096,
                128000
        );
        memoryManager.setProjectPath(tempDir.toString());
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.setProjectPath(tempDir.toString());
        PlanExecuteAgent agent = new PlanExecuteAgent(
                llmClient,
                toolRegistry,
                new StubPlanner(llmClient),
                memoryManager,
                (goal, plan) -> PlanExecuteAgent.PlanReviewDecision.execute()
        );

        String result = agent.run("请读取测试文件并确认内容");

        assertTrue(result.contains("计划执行完成"));
        assertTrue(memoryManager.listLongTerm().isEmpty());
    }

    @Test
    void shouldNotExtractFactsWhenPlanIsCanceled() throws Exception {
        StubGLMClient llmClient = new StubGLMClient(List.of());
        MemoryManager memoryManager = new MemoryManager(
                llmClient,
                4096,
                128000
        );
        PlanExecuteAgent agent = new PlanExecuteAgent(
                llmClient,
                new ToolRegistry(),
                new StubPlanner(llmClient),
                memoryManager,
                (goal, plan) -> PlanExecuteAgent.PlanReviewDecision.cancel()
        );

        String result = agent.run("列出当前目录的文件");

        assertEquals("⏹️ 已取消本次计划执行。", result);
        assertTrue(memoryManager.listLongTerm().isEmpty());
    }

    @Test
    void shouldNotRepeatStreamedTaskOutputInFinalPlanSummary() throws Exception {
        StubGLMClient llmClient = StubGLMClient.streaming(List.of(
                StubResponse.streamed(new LlmClient.ChatResponse(
                        "assistant",
                        "当前目录包含 8 个目录和 8 个文件。",
                        null,
                        60,
                        20
                ))
        ));

        PlanExecuteAgent agent = new PlanExecuteAgent(
                llmClient,
                new ToolRegistry(),
                new StubPlanner(llmClient),
                null,
                (goal, plan) -> PlanExecuteAgent.PlanReviewDecision.execute()
        );

        String result = agent.run("列出当前目录的文件");

        assertEquals("✅ 计划执行完成！", result);
    }

    @Test
    void shouldNotPrintEmptyTaskReasoningHeadingAndShouldUseOutputLabel() throws Exception {
        StubGLMClient llmClient = StubGLMClient.streaming(List.of(
                StubResponse.scripted(
                        listener -> {
                            listener.onReasoningDelta("  \n");
                            listener.onContentDelta("我来读取 pom.xml 文件。");
                        },
                        new LlmClient.ChatResponse(
                                "assistant",
                                "我来读取 pom.xml 文件。",
                                "  \n",
                                null,
                                60,
                                20
                        )
                )
        ));

        PlanExecuteAgent agent = new PlanExecuteAgent(
                llmClient,
                new ToolRegistry(),
                new StubPlanner(llmClient),
                null,
                (goal, plan) -> PlanExecuteAgent.PlanReviewDecision.execute()
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            agent.run("读取 pom.xml");
        } finally {
            System.setOut(originalOut);
        }

        String rendered = output.toString(StandardCharsets.UTF_8);
        assertFalse(rendered.contains("任务思考 [task_1]"),
                "空白 reasoning 不应打印空的任务思考标题: " + rendered);
        assertTrue(rendered.contains("任务输出 [task_1]"));
        assertFalse(rendered.contains("任务结果 [task_1]"),
                "tool-call 前后的流式 content 不应被误标成任务结果: " + rendered);
    }

    @Test
    void splitsParallelTasksWhenResourceLocksConflict() {
        StubGLMClient llmClient = new StubGLMClient(List.of());
        PlanExecuteAgent agent = new PlanExecuteAgent(
                llmClient,
                new ToolRegistry(),
                new StubPlanner(llmClient),
                null,
                (goal, plan) -> PlanExecuteAgent.PlanReviewDecision.execute()
        );

        Task first = new Task("task_1", "write file", Task.TaskType.FILE_WRITE);
        first.setResourceLocks(List.of("file:src/App.java"));
        Task second = new Task("task_2", "write same file", Task.TaskType.FILE_WRITE);
        second.setResourceLocks(List.of("file:src/App.java"));
        Task third = new Task("task_3", "read pom", Task.TaskType.FILE_READ);

        List<List<Task>> batches = agent.splitIntoResourceSafeBatches(List.of(first, second, third));

        assertEquals(List.of(first, third), batches.get(0));
        assertEquals(List.of(second), batches.get(1));
        assertTrue(agent.getLastTrace().events().stream()
                .anyMatch(event -> event.type().equals("resource.conflict")
                        && "task_2".equals(event.taskId())));
        assertTrue(agent.getLastTrace().events().stream()
                .anyMatch(event -> event.type().equals("resource.batch.split")
                        && "2".equals(event.attributes().get("batches"))));
    }

    @Test
    void splitsParallelFileWritesWhenPlannerOmittedExplicitLocks() {
        StubGLMClient llmClient = new StubGLMClient(List.of());
        PlanExecuteAgent agent = new PlanExecuteAgent(
                llmClient,
                new ToolRegistry(),
                new StubPlanner(llmClient),
                null,
                (goal, plan) -> PlanExecuteAgent.PlanReviewDecision.execute()
        );

        Task first = new Task("task_1", "update src/main/java/com/tcode/App.java", Task.TaskType.FILE_WRITE);
        Task second = new Task("task_2", "rewrite src/main/java/com/tcode/App.java", Task.TaskType.FILE_WRITE);
        Task third = new Task("task_3", "update src/test/java/com/tcode/AppTest.java", Task.TaskType.FILE_WRITE);

        List<List<Task>> batches = agent.splitIntoResourceSafeBatches(List.of(first, second, third));

        assertEquals(List.of(first, third), batches.get(0));
        assertEquals(List.of(second), batches.get(1));
    }

    @Test
    void splitsParallelTasksWhenFileAndDirectoryLocksOverlap() {
        StubGLMClient llmClient = new StubGLMClient(List.of());
        PlanExecuteAgent agent = new PlanExecuteAgent(
                llmClient,
                new ToolRegistry(),
                new StubPlanner(llmClient),
                null,
                (goal, plan) -> PlanExecuteAgent.PlanReviewDecision.execute()
        );

        Task directoryWriter = new Task("task_1", "rewrite package", Task.TaskType.FILE_WRITE);
        directoryWriter.setResourceLocks(List.of("dir:src/main/java/com/tcode"));
        Task fileWriter = new Task("task_2", "update src/main/java/com/tcode/App.java", Task.TaskType.FILE_WRITE);
        Task unrelatedWriter = new Task("task_3", "update src/test/java/com/tcode/AppTest.java", Task.TaskType.FILE_WRITE);

        List<List<Task>> batches = agent.splitIntoResourceSafeBatches(List.of(directoryWriter, fileWriter, unrelatedWriter));

        assertEquals(List.of(directoryWriter, unrelatedWriter), batches.get(0));
        assertEquals(List.of(fileWriter), batches.get(1));
    }

    @Test
    void recordsPlanAndTaskTraceEvents() throws Exception {
        StubGLMClient llmClient = new StubGLMClient(List.of(
                new LlmClient.ChatResponse("assistant", "done", null, 10, 5)
        ));
        PlanExecuteAgent agent = new PlanExecuteAgent(
                llmClient,
                new ToolRegistry(),
                new StubPlanner(llmClient),
                null,
                (goal, plan) -> PlanExecuteAgent.PlanReviewDecision.execute()
        );

        agent.run("read file");

        assertTrue(agent.getLastTrace().events().stream().anyMatch(event -> event.type().equals("plan.created")));
        assertTrue(agent.getLastTrace().events().stream().anyMatch(event -> event.type().equals("task.started")));
        assertTrue(agent.getLastTrace().events().stream().anyMatch(event -> event.type().equals("task.completed")));
    }

    private record StubResponse(LlmClient.ChatResponse response, boolean streamContent,
                                java.util.function.Consumer<LlmClient.StreamListener> streamScript) {
        private static StubResponse plain(LlmClient.ChatResponse response) {
            return new StubResponse(response, false, null);
        }

        private static StubResponse streamed(LlmClient.ChatResponse response) {
            return new StubResponse(response, true, null);
        }

        private static StubResponse scripted(java.util.function.Consumer<LlmClient.StreamListener> streamScript,
                                             LlmClient.ChatResponse response) {
            return new StubResponse(response, false, streamScript);
        }
    }

    private static final class StubPlanner extends Planner {
        private StubPlanner(LlmClient llmClient) {
            super(llmClient);
        }

        @Override
        public ExecutionPlan createPlan(String goal) {
            ExecutionPlan plan = new ExecutionPlan("plan-test", goal);
            plan.addTask(new Task("task_1", "读取测试文件", Task.TaskType.FILE_READ));
            plan.computeExecutionOrder();
            return plan;
        }
    }

    private static final class StubGLMClient extends GLMClient {
        private final Queue<StubResponse> responses;

        private StubGLMClient(List<ChatResponse> responses) {
            super("test-key");
            this.responses = new ArrayDeque<>(responses.stream().map(StubResponse::plain).toList());
        }

        private StubGLMClient(Queue<StubResponse> responses) {
            super("test-key");
            this.responses = responses;
        }

        private static StubGLMClient streaming(List<StubResponse> responses) {
            return new StubGLMClient(new ArrayDeque<>(responses));
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            return chat(messages, tools, StreamListener.NO_OP);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException {
            StubResponse stubResponse = responses.poll();
            if (stubResponse == null) {
                throw new IOException("缺少预设响应");
            }
            if (stubResponse.streamScript() != null) {
                stubResponse.streamScript().accept(listener);
            } else if (stubResponse.streamContent() && stubResponse.response().content() != null) {
                listener.onContentDelta(stubResponse.response().content());
            }
            return stubResponse.response();
        }
    }
}
