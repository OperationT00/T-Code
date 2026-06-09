package com.tcode.cli;

import com.tcode.agent.Agent;
import com.tcode.agent.AgentOrchestrator;
import com.tcode.agent.PlanExecuteAgent;
import com.tcode.browser.BrowserConnectivityCheck;
import com.tcode.browser.BrowserSession;
import com.tcode.config.TCodeConfig;
import com.tcode.hitl.HitlToolRegistry;
import com.tcode.hitl.SwitchableHitlHandler;
import com.tcode.hitl.TerminalHitlHandler;
import com.tcode.llm.LlmClient;
import com.tcode.llm.LlmClientFactory;
import com.tcode.render.Renderer;
import com.tcode.render.inline.InlineRenderer;
import com.tcode.mcp.McpServerManager;
import com.tcode.mcp.mention.AtMentionExpander;
import com.tcode.runtime.task.DurableTaskManager;
import com.tcode.snapshot.SnapshotService;
import com.tcode.skill.SkillRegistry;
import com.tcode.tool.ToolRegistry;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.reader.LineReader;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * t-code v1.0.0 - Terminal-First Agent IDE
 * 支持 ReAct、Plan-and-Execute、Memory、Multi-Agent、HITL、并行工具调用、多模型切换、MCP、CDP 会话复用
 */
public class Main {
    public static void main(String[] args) {
        CliEnvironmentConfig.configureAwtForCli();
        if (CliRuntimeApiLauncher.isServeCommand(args)) {
            CliEnvironmentConfig.configureLogging();
            CliRuntimeApiLauncher.startAndBlock(args);
            return;
        }

        CliEnvironmentConfig.configureLogging();

        TCodeConfig config = TCodeConfig.load();
        LlmClient llmClient = LlmClientFactory.createFromConfig(config);
        if (llmClient == null) {
            System.err.println("❌ 错误: 未找到可用的 API Key");
            System.err.println("请在 .env 文件中添加 GLM_API_KEY、DEEPSEEK_API_KEY、STEP_API_KEY 或 KIMI_API_KEY");
            System.exit(1);
        }
        AtomicReference<LlmClient> llmClientRef = new AtomicReference<>(llmClient);

        try (Terminal terminal = TerminalBuilder.builder().system(true).dumb(true).build()) {
            CliSessionInfrastructure infrastructure = CliSessionInfrastructure.create(Path.of("."));
            TerminalHitlHandler terminalHitlHandler = infrastructure.terminalHitlHandler();
            SwitchableHitlHandler hitlHandler = infrastructure.hitlHandler();
            HitlToolRegistry hitlToolRegistry = infrastructure.hitlToolRegistry();
            BrowserSession browserSession = infrastructure.browserSession();
            BrowserConnectivityCheck browserConnectivityCheck = infrastructure.browserConnectivityCheck();
            McpServerManager mcpServerManager = infrastructure.mcpServerManager();
            AtomicReference<SkillRegistry> skillRegistryRef = new AtomicReference<>();

            LineReader lineReader = CliLineReaderFactory.create(
                    terminal,
                    mcpServerManager::resourceCandidates,
                    () -> skillRegistryRef.get() == null ? List.of() : skillRegistryRef.get().allSkills(),
                    Path.of(System.getProperty("user.home")));

            // JLine-first：启动输出、命令输出、Agent 流式内容都走同一条 Renderer.stream() 通道。
            // inline 首屏要挂到 LineReader 首次初始化回调里，避免在 readLine 接管屏幕前用裸输出抢光标。
            CliRendererInfrastructure rendererInfrastructure = CliRendererInfrastructure.start(
                    terminal, lineReader, hitlHandler,
                    CliStartupStatus.statusInfo(llmClient, hitlHandler, "idle", mcpServerManager, null));
            Renderer renderer = rendererInfrastructure.renderer();
            PrintStream ui = rendererInfrastructure.ui();

            Path home = Path.of(System.getProperty("user.home"));
            CliMcpInfrastructure mcpInfrastructure = CliMcpInfrastructure.start(
                    home, Path.of("."), mcpServerManager, ui, CliStartupStatus.mcpStartupWait(),
                    Runtime.getRuntime()::addShutdownHook);
            String startupNote = mcpInfrastructure.startupNote();
            AtMentionExpander mentionExpander = mcpInfrastructure.mentionExpander();
            LocalPathMentionExpander localPathMentionExpander = mcpInfrastructure.localPathMentionExpander();

            // === Skill 系统初始化 ===
            CliSkillInfrastructure skillInfrastructure = CliSkillInfrastructure.create(home, Path.of("."));
            startupNote = CliStartupStatus.appendStartupNote(startupNote, skillInfrastructure.startupNote());
            SkillRegistry skillRegistry = skillInfrastructure.skillRegistry();
            com.tcode.skill.SkillStateStore skillStateStore = skillRegistry.stateStore();
            skillRegistryRef.set(skillRegistry);
            com.tcode.skill.SkillContextBuffer skillContextBuffer = skillInfrastructure.skillContextBuffer();
            Agent reactAgent = CliAgentFactory.create(
                    llmClient, hitlToolRegistry, mcpServerManager, skillRegistry, skillContextBuffer);
            CliTaskInfrastructure taskInfrastructure = CliTaskInfrastructure.start(
                    prompt -> CliRuntimeApiLauncher.runHeadlessTask(prompt, llmClientRef.get()),
                    Runtime.getRuntime()::addShutdownHook);
            DurableTaskManager taskManager = taskInfrastructure.taskManager();
            renderer.updateStatus(CliStartupStatus.statusInfo(llmClient, hitlHandler, "idle", mcpServerManager, skillRegistry));
            CliPresentation.StartupScreenInfo startupScreenInfo = CliStartupStatus.startupScreenInfo(llmClient, mcpServerManager, skillRegistry, startupNote);
            CliInteractiveUiInstaller.installStartupScreen(renderer, ui, CliPresentation.startupScreenLines(startupScreenInfo));
            CliExecutionModeState executionMode = new CliExecutionModeState();

            // === TUI / CLI 分支判断 ===
            // 旧 TCODE_TUI=true 路径仍走 Lanterna 全屏 TUI（Day 5 后由 LanternaRenderer 接管）。
            if (com.tcode.tui.TuiBootstrap.shouldUseTui(terminal)) {
                try {
                    com.tcode.tui.TuiBootstrap.launch(config, llmClient, reactAgent, hitlHandler);
                    return;  // TUI 启动成功，不进入 CLI 循环
                } catch (Exception e) {
                    hitlHandler.setDelegate(terminalHitlHandler);
                    System.err.println("❌ TUI 启动失败，降级到 CLI: " + e.getMessage());
                    e.printStackTrace();
                    // 降级到 CLI 继续执行
                }
            }

            reactAgent.setRenderer(renderer);
            reactAgent.setHitlEnabledSupplier(hitlHandler::isEnabled);
            reactAgent.getToolRegistry().setWriteFileObserver(
                    (path, ba) -> renderer.appendDiff(path, ba[0], ba[1]));

            // Day 3：inline 模式绑 Ctrl+O 到 BlockRegistry.toggleLast 实现折叠块展开/收起
            boolean spaciousPrompt = CliInteractiveUiInstaller.installRuntimeWidgets(renderer, lineReader);
            CliControlCommandDispatcher.Context controlCommandContext =
                    new CliControlCommandDispatcher.Context(
                            ui,
                            lineReader,
                            hitlHandler,
                            mcpServerManager,
                            browserSession,
                            browserConnectivityCheck,
                            hitlToolRegistry,
                            taskManager,
                            skillRegistry,
                            skillStateStore,
                            reactAgent.getMemoryManager(),
                            reactAgent.getToolRegistry(),
                            () -> renderer.updateStatus(CliStartupStatus.statusInfo(
                                    llmClientRef.get(), hitlHandler, "idle", mcpServerManager, skillRegistry)));
            CliModelCommandDispatcher.Context modelCommandContext =
                    new CliModelCommandDispatcher.Context(
                            ui,
                            config,
                            llmClientRef::get,
                            reactAgent,
                            client -> renderer.updateStatus(CliStartupStatus.statusInfo(
                                    client, hitlHandler, "idle", mcpServerManager, skillRegistry)));
            CliConfigCommandDispatcher.Context configCommandContext =
                    new CliConfigCommandDispatcher.Context(
                            renderer, config, llmClientRef::get, hitlHandler, skillRegistry);
            CliConversationCommandDispatcher.Context conversationCommandContext =
                    new CliConversationCommandDispatcher.Context(ui, reactAgent, hitlHandler);

            while (true) {
                CliPromptInput.PromptInput promptInput;
                try {
                    promptInput = CliPromptInput.read(terminal, lineReader, renderer,
                            executionMode.hasPendingMode(), spaciousPrompt);
                } catch (UserInterruptException e) {
                    continue;  // Ctrl+C 跳过
                } catch (EndOfFileException e) {
                    break;  // Ctrl+D 退出
                }
                if (renderer instanceof InlineRenderer inline) {
                    inline.clearAcceptedInput(promptInput.text());
                }

                if (promptInput.canceled()) {
                    CliExecutionModeState.Mode canceledMode = executionMode.cancelPending();
                    if (canceledMode == CliExecutionModeState.Mode.PLAN) {
                        ui.println("↩️ 已取消待执行的 Plan-and-Execute，回到默认 ReAct。\n");
                    }
                    if (canceledMode == CliExecutionModeState.Mode.TEAM) {
                        ui.println("↩️ 已取消待执行的 Multi-Agent，回到默认 ReAct。\n");
                    }
                    continue;
                }

                String input = promptInput.text().trim();

                if (input.isEmpty()) {
                    continue;
                }

                CliCommandParser.ParsedCommand command = CliCommandParser.parse(input);
                boolean submittedInputRendered = false;
                if (command.type() != CliCommandParser.CommandType.NONE) {
                    renderer.beginTurn();
                    CliSubmittedInput.print(renderer, ui, input);
                    submittedInputRendered = true;
                }
                if (CliControlCommandDispatcher.dispatch(command, controlCommandContext)) {
                    continue;
                }
                CliModelCommandDispatcher.Result modelCommandResult =
                        CliModelCommandDispatcher.dispatch(command, modelCommandContext);
                if (modelCommandResult.handled()) {
                    llmClient = modelCommandResult.client();
                    llmClientRef.set(llmClient);
                    continue;
                }
                if (CliConfigCommandDispatcher.dispatch(command, configCommandContext)) {
                    continue;
                }
                if (CliConversationCommandDispatcher.dispatch(command, conversationCommandContext)) {
                    continue;
                }
                switch (command.type()) {
                    case UNKNOWN_COMMAND -> {
                        ui.println("❌ 未知命令: " + command.payload());
                        CliPresentation.printSlashCommandHelp(ui);
                        continue;
                    }
                    case EXIT -> {
                        ui.println("\n👋 再见!");
                        renderer.close();
                        return;
                    }
                    case SWITCH_PLAN -> {
                        if (command.payload() == null || command.payload().isEmpty()) {
                            executionMode.activate(CliExecutionModeState.Mode.PLAN);
                            ui.println("📋 下一条任务将使用 Plan-and-Execute 模式，输入任务前按 ESC 可取消，执行完成后自动回到默认 ReAct。\n");
                            continue;
                        }
                        input = command.payload();
                    }
                    case SWITCH_TEAM -> {
                        if (command.payload() == null || command.payload().isEmpty()) {
                            executionMode.activate(CliExecutionModeState.Mode.TEAM);
                            ui.println("👥 下一条任务将使用 Multi-Agent 协作模式（规划者 + 执行者 + 检查者），输入任务前按 ESC 可取消，执行完成后自动回到默认 ReAct。\n");
                            continue;
                        }
                        input = command.payload();
                    }
                    case NONE -> {
                    }
                }

                // 运行 Agent
                String submittedInput = input;
                input = mentionExpander.expand(input);
                input = localPathMentionExpander.expand(input);
                if (!(renderer instanceof InlineRenderer)) {
                    ui.println();
                }
                if (!submittedInputRendered) {
                    renderer.beginTurn();
                    CliSubmittedInput.print(renderer, ui, submittedInput);
                }
                final String taskInput = input;
                Callable<String> runTask;
                CliExecutionModeState.Mode activeMode = executionMode.modeFor(command.type());
                String snapshotMode = activeMode.snapshotName();
                if (activeMode == CliExecutionModeState.Mode.PLAN) {
                    LlmClient activeClient = llmClient;
                    runTask = () -> {
                        PlanExecuteAgent planAgent = CliAgentFactory.createInteractivePlan(
                                activeClient, reactAgent, terminal, lineReader, ui);
                        planAgent.setExternalContextSupplier(mcpServerManager::resourceIndexForPrompt);
                        planAgent.setSkillRegistry(skillRegistry);
                        planAgent.setSkillContextBuffer(skillContextBuffer);
                        return planAgent.run(taskInput);
                    };
                } else if (activeMode == CliExecutionModeState.Mode.TEAM) {
                    LlmClient activeClient = llmClient;
                    runTask = () -> {
                        AgentOrchestrator orchestrator = CliAgentFactory.createTeam(activeClient, reactAgent, ui);
                        orchestrator.setExternalContextSupplier(mcpServerManager::resourceIndexForPrompt);
                        orchestrator.setSkillSystem(skillRegistry, skillContextBuffer);
                        return orchestrator.run(taskInput);
                    };
                } else {
                    runTask = () -> reactAgent.run(taskInput);
                }
                SnapshotService snapshotService = reactAgent.getToolRegistry().getSnapshotService();
                renderer.updateStatus(CliStartupStatus.statusInfo(llmClient, hitlHandler, snapshotMode, mcpServerManager, skillRegistry));
                String response = CliTaskRunner.run(terminal,
                        () -> snapshotService.runTurn(snapshotMode, taskInput, runTask::call));
                if (!"react".equals(snapshotMode)) {
                    renderer.updateStatus(CliStartupStatus.statusInfo(llmClient, hitlHandler, "idle", mcpServerManager, skillRegistry));
                }
                executionMode.reset();
                if (response != null && !response.isBlank()) {
                    ui.println(response);
                    ui.println();
                }
            }
            ui.println("\n👋 再见!");
            renderer.close();

        } catch (IOException e) {
            System.err.println("❌ 终端初始化失败: " + e.getMessage());
            System.exit(1);
        }
    }

}
