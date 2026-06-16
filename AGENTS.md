# AGENTS.md

仓库给 Agent / 新线程使用的首读入口。详细行为描述见 `docs/agents-reference.md`。

## 信息优先级

1. 代码实际行为 > 2. `AGENTS.md` > 3. `README.md` > 4. `CLAUDE.md`

## 项目快照

- 项目名：`t-code`
- 定位：面向商业使用的 Java Agent CLI 产品，对标 Claude Code
- 已交付 21 期（ReAct → Plan+DAG → Memory → RAG → Multi-Agent → HITL → 并行工具 → 多模型 → 联网 → MCP 核心 → MCP 高级 → 长上下文 → Chrome DevTools → CDP 会话复用 → Skill → TUI → LSP 诊断 → Side-Git 快照 → Prompt 分层 → Runtime API → 图片输入）
- 下一步：OAuth / sampling / recovery 作为后续 MCP 增强
- 公开版本：`v1.0.2`，Maven 产物：`t-code-1.0-SNAPSHOT.jar`

## 运行前提

- Java 17+ / Maven
- 至少一个 API Key：`GLM_API_KEY` / `DEEPSEEK_API_KEY` / `STEP_API_KEY` / `KIMI_API_KEY`

## 常用命令

```bash
cp .env.example .env
mvn clean package        # 默认跳过测试，优先产出可手工验收 jar
java -jar target/t-code-1.0-SNAPSHOT.jar
mvn test -Pquick          # 常规回归
mvn test -Pphase16-smoke  # TUI 相关
mvn test -Dtest=XxxTest -DskipTests=false   # 针对性
mvn test -DskipTests=false                  # 全量回归
```

## 架构概览

三条主执行路径，共享 ToolRegistry / MemoryManager / SnapshotService：

| 路径 | 入口 | 触发 |
|------|------|------|
| ReAct | `Agent.java` | 默认模式 |
| Plan-and-Execute | `PlanExecuteAgent.java` | `/plan` |
| Multi-Agent | `AgentOrchestrator.java` | `/team` |

Plan-and-Execute now validates generated DAG plans before execution, attaches a conservative estimate
(tasks / batches / effort / minutes / risk) to the review screen, normalizes resource locks, infers
specific file write locks from task descriptions when planner metadata is incomplete, treats `dir:<path>`
locks as conflicting with files under that directory, splits ready tasks by resource locks before
parallel execution, records resource conflict/batch-split trace events,
classifies failures before retry/replan/stop decisions, and keeps an in-memory `PlanRunTrace` for
plan/task/tool events. Explicit `resource_locks` remain preferred; missing file paths fall back to
coarse locks such as `tool:file-write`. `PlanRecoveryBudget` caps per-task retries, per-run replans,
and total recovery actions so retry/replan cannot loop indefinitely; repeated failures are recorded as
`plan.recovery.stuck` before recovery stops. Replanned plans are returned to the review loop instead
of recursively invoking plan execution, and `plan.replan.created` records each generated replacement plan.
Replan failure context includes the failed task, completed/failed task summaries, retry/replan counts,
and an explicit instruction to avoid repeating the same failed approach. `PlanFailureClassifier` returns
`RecoveryDecision` with action, reason, and user-intervention hint; recovery budget defaults can be
overridden with `tcode.plan.recovery.*` system properties or `TCODE_PLAN_RECOVERY_*` environment variables.

核心内置工具 10 个：`read_file` / `write_file` / `list_dir` / `glob_files` / `grep_code` / `execute_command` / `create_project` / `web_search` / `web_fetch` / `revert_turn`

`ToolRegistry` 已提供 `ToolProvider` / `ToolRegistrationContext` 扩展口；内置工具声明已全部迁移为独立 provider（File / FileSearch / Project / Shell / Web / Browser / Memory / Skill / Snapshot）。文件操作、项目脚手架、Shell、实时文件搜索与 Web 访问实现已分别下沉到 `FileService`、`ProjectScaffolder`、`ShellService`、`FileSearchService` 和 `WebService`；其余实现可继续从 Registry 私有方法逐步下沉，不要把新工具声明直接堆进 `ToolRegistry` 大类。

Browser / Memory / Skill 的可变依赖统一通过 `ToolRuntimeBindings` 管理；Provider 注册、参数 schema 和 LLM 工具定义导出统一通过 `ToolDefinitionCatalog` 管理；MCP 动态工具 descriptor / invoker 目录统一通过 `McpToolCatalog` 管理；并行工具批次、顺序回收、超时和取消处理统一通过 `ToolBatchExecutor` 管理；单工具执行、审计和浏览器策略挂钩统一通过 `ToolExecutionPipeline` 管理。`ToolRegistry` 对外保留兼容 setter、MCP 注册 API、`executeTool()` 与 `executeTools()`，内部主要负责组装和 facade 转发。

Tool calling 治理层已补齐基础工程化能力：`ToolExecutionPipeline` 会在执行前按工具 JSON schema 做轻量参数校验（object / required / primitive type / enum），无效 JSON、缺必填或类型错误会返回 `INVALID_ARGUMENTS`，不会进入真实 executor；`ToolOutput` 携带 `ToolCallStatus`、`ToolErrorCode`、retryable、耗时和 attempts，旧 `executeTool()` 仍兼容返回纯文本；`ToolBatchExecutor` 会先按资源锁把同轮 tool calls 拆成无冲突批次（文件/目录/workspace、Shell、Browser、MCP server、Memory、Skill context），批次内最多 4 并发、批次间串行、最终结果仍按原始 tool_call 顺序回收，并对 retryable 失败默认重试一次；`ToolRegistry.recentToolTraceEvents()` 保留最近工具 trace，用于本地诊断和评测入口。

`CoreRuntime` 会把 `ToolRegistry` 生命周期事件桥接到 Runtime API：工具执行前发 `tool.started`，执行完成后发 `tool.completed`。配置 HITL handler 时，还会桥接 `hitl.requested` / `hitl.resolved` 观察事件。核心事件 payload 统一通过 `RuntimeEventPayloads` 构造并携带 `schema_version: 1`；TypeScript CLI 解析 SSE 时显式暴露 `schemaVersion`，缺少版本的旧事件按版本 `1` 兼容，并使用 thread 级 `RuntimeEventCursor` 增量消费事件，不能在每轮 turn 内把 `after` 重置为 `0`。HTTP 模式使用 `RuntimeHitlHandler` 保存待审批项，开放 `GET /v1/approvals` 与 `POST /v1/approvals/{id}/decision`；成功与错误 JSON 响应统一通过 `RuntimeApiResponses` 构造，错误格式为 `{"error":{"code":"...","message":"..."}}`。`RuntimeApiServer` 只保留 HTTP 适配；thread / turn / events 和 approval 路由分别由 `RuntimeThreadRoutes` 与 `RuntimeApprovalRoutes` 管理，并通过 `RuntimeApiRequest` / `RuntimeApiResult` 与网络层连接；Bearer 与 `X-TCode-API-Key` 鉴权规则、API key 配置读取统一由 `RuntimeApiAuthPolicy` 管理。TypeScript CLI 通过 `RuntimeApiError` 兼容消费新结构和旧字符串错误，并通过 `RuntimeRequestRetrier` 只重试 events / approvals 的幂等 GET 网络异常；`createThread`、`submitTurn`、审批决策写请求不得自动重放。事件监听属于 fail-soft 观察侧，写事件失败不能阻断工具主路径。

交互 CLI 的 ReAct / Plan / Team Agent 装配统一走 `CliAgentFactory`：共享现有 `ToolRegistry` 与 `MemoryManager`，注入 MCP resource prompt 上下文，并把 Skill registry / buffer 同步到工具层和 Agent 层。不要在 `Main` 中重新散落这组装配逻辑。

交互 CLI 的 HITL、Browser session、Browser guard、Browser connector 与 MCP manager 统一由 `CliSessionInfrastructure` 创建。`Main` 负责按启动阶段加载和启动 MCP server，但不再直接拼装这些基础设施依赖。

交互 CLI 的 Skill 缓存目录、内置 Skill 解压、状态存储、registry reload 与 context buffer 创建统一由 `CliSkillInfrastructure` 管理。`Main` 只消费初始化结果，并把可选启动提示合并进首屏。

交互 CLI 的 MCP 配置 bootstrap、server 加载与限时启动、shutdown hook、MCP resource mention expander 和本地路径 mention expander 统一由 `CliMcpInfrastructure` 管理。后台任务 manager 的打开、启动和 shutdown hook 统一由 `CliTaskInfrastructure` 管理。

交互 CLI 的 `LineReader` 构造统一走 `CliLineReaderFactory`：持久化 history、completer、highlighter、基础 option 和 slash / autosuggestion / autopair widgets 在这里集中配置。Renderer 绑定和运行期快捷键仍按启动阶段留在 `Main`。

交互 CLI 的 Renderer 创建、Renderer HITL delegate、inline LineReader 绑定、`Renderer.start()` 与首个状态更新统一由 `CliRendererInfrastructure` 管理。首屏内容安装和运行期快捷键仍按启动阶段留在 `Main`。

交互 CLI 的首屏安装和运行期快捷键 wiring 统一由 `CliInteractiveUiInstaller` 管理：inline 模式把首屏挂到 `CALLBACK_INIT`，降级渲染器直接输出；运行期为 inline 绑定 Ctrl+O，并为所有模式绑定 Ctrl+V 与 Esc。

交互 CLI 的低耦合控制命令统一由 `CliControlCommandDispatcher` 分发：输入历史清理、HITL 开关、Memory、Policy、Audit、Snapshot、MCP 管理、Browser、后台 Task 和 Skill 管理不再堆在 `Main` 的主循环中。会改变 Agent 执行模式或依赖对话状态的命令仍由 `Main` 协调。

交互 CLI 的模型切换统一由 `CliModelCommandDispatcher` 分发：`/model` 状态展示、provider 解析、client 创建、配置保存与 Agent client 更新不再堆在 `Main`。只读 `/config` palette 统一由 `CliConfigCommandDispatcher` 分发。

交互 CLI 的 `/browser` 子命令实现统一由 `CliBrowserCommandHandler` 管理：status、autoConnect、旧式端口连接、disconnect 与 tabs 不再作为 `Main` 私有 helper；`CliControlCommandDispatcher` 和 `CliSessionInfrastructure` 共享同一入口。

交互 CLI 的会话级命令统一由 `CliConversationCommandDispatcher` 分发：`/cancel`、`/clear`、`/context`、`/compact [focus]`、`/context events`、`/context recall <keyword>`、`/context show <event_id>` 与 `/context inject <event_id>` 不再堆在 `Main` 的主循环中。`Main` 的命令 switch 只保留 unknown、exit 与 Plan/Team 模式协调。

交互 CLI 的静态展示内容统一由 `CliPresentation` 管理：startup hints、slash command catalog/help/choices 与 startup banner lines 不再堆在 `Main`；completer 与 LineReader tail tips 共用同一命令目录。

交互 CLI 的启动状态构造统一由 `CliStartupStatus` 管理：首屏模型 / MCP / Skill 摘要、底部 dock 状态、启动 note 合并与 MCP 限时等待配置不再堆在 `Main`。

交互 CLI 的 JLine widget 统一由 `CliInputWidgets` 管理：slash 输入、autosuggestion / autopair、tail tips、Ctrl+O 折叠、Ctrl+V 图片注入与 Esc 清空输入不再堆在 `Main`。

交互 CLI 的输入历史统一由 `CliInputHistory` 管理：持久化 history 配置、文件路径归一化、方向键预填充与 `/history clear` 清理不再堆在 `Main`。

交互 CLI 的输入规范化统一由 `CliInputNormalization` 管理：换行归一化、bracketed paste marker 处理、提交键判断与 ESC sequence 分类不再堆在 `Main`。

交互 CLI 的终端 raw input 统一由 `CliTerminalInput` 管理：任务期 ESC 监听、单键读取、prefill、bracketed paste burst 与 raw mode 结果对象不再堆在 `Main`。

交互 CLI 的前台任务执行统一由 `CliTaskRunner` 管理：CancellationContext、executor、Future 生命周期、任务期 ESC 取消与错误文本格式化不再堆在 `Main`。

交互 CLI 的 Prompt 输入协调统一由 `CliPromptInput` 管理：prompt / right prompt、beforeInput / afterInput、Esc prefill 与 spacious 模式判断不再堆在 `Main`。

交互 CLI 的 Plan 审阅交互统一由 `CliPlanReviewHandler` 管理：Enter 执行、Ctrl+O 展开、ESC 折叠或取消、I 补充重规划、行输入回退与 parser decision 映射不再堆在 `Main`。

CLI 的 Runtime API 启动入口统一由 `CliRuntimeApiLauncher` 管理：`serve --http` 识别、端口解析、阻塞启动和后台任务使用的 headless 单轮执行不再堆在 `Main`。

CLI 的环境初始化统一由 `CliEnvironmentConfig` 管理：macOS AWT headless、日志属性默认值、`~` 展开和 `.env` 配置读取不再堆在 `Main`。

交互 CLI 的提交回显统一由 `CliSubmittedInput` 管理：inline renderer 分流、plain transcript 块和终端宽度回退不再堆在 `Main`。

交互 CLI 的待执行模式状态统一由 `CliExecutionModeState` 管理：ReAct / Plan / Team 选择、ESC 取消和任务完成后的重置不再由多个布尔变量拼接。

跨平台输出约定：`glob_files` / `grep_code` 与 Memory 项目键统一使用 `/` 展示路径；`execute_command` 在 Windows 使用 PowerShell，在其他平台使用 `bash`。

代码库理解默认走 Claude Code 式实时探索：`glob_files` 找候选文件、`grep_code` 精确定位符号或字符串、`read_file` 按需读取具体行段，并结合 `execute_command` / LSP 诊断做验证。不再在核心路径内维护 RAG 代码索引。

MCP 动态工具：`mcp__{server}__{tool}`（+ resources 虚拟工具）

## 仓库结构

```
src/main/java/com/tcode/
├── agent/       Agent.java, PlanExecuteAgent.java, SubAgent.java, AgentOrchestrator.java
├── cli/         Main.java, CliCommandParser.java, PlanReviewInputParser.java
├── browser/     BrowserSession, BrowserGuard, SensitivePagePolicy
├── llm/         GLMClient, DeepSeekClient, StepClient, KimiClient
├── context/     ContextProfile, ContextMode, TokenUsageFormatter, ContextManager, ConversationHistoryCompactor
├── memory/      MemoryManager, MarkdownMemoryStore, MemoryScope
├── plan/        Planner, ExecutionPlan, Task
├── lsp/         LspManager, LspDiagnosticFormatter
├── prompt/      PromptAssembler, PromptContext, PromptRepository
├── image/       ImageReferenceParser
├── runtime/     api/ (RuntimeApiServer) + task/ (DurableTaskManager)
├── snapshot/    SideGitManager, SnapshotService
├── tool/        ToolRegistry
├── mcp/         McpClient, McpServerManager, transport/, resources/, mention/
├── hitl/        HitlToolRegistry, ApprovalPolicy, TerminalHitlHandler
├── web/         SearchProvider, WebFetcher, HtmlExtractor, NetworkPolicy
├── policy/      PathGuard, CommandGuard, AuditLog
├── skill/       SkillRegistry, SkillContextBuffer, SkillIndexFormatter
└── render/      Renderer, InlineRenderer, PlainRenderer, RendererFactory

clients/
└── t-code-cli/  TypeScript thin client，调用 Java Runtime API
```

启动与 inline 渲染当前约定：

- 开屏 Banner 使用无右边框的简洁布局，避免 CJK/ANSI 字宽导致右侧竖线错位；默认是大写 `T` 彩色 logo + Qoder 风格首屏，只展示模型、MCP、Skill、ReAct 状态和三条 getting-started tips，不再把 MCP server 明细刷成启动日志。
- inline 模式使用 JLine 4 的 LineReader 编辑能力，默认提示符是 `* `，右提示显示 `message / @path / @image`。
- 默认 CLI 启动路径应先 `Renderer.start()` 并初始化底部 dock；inline 首屏不要在 `readLine` 前裸写 stdout，而是通过 `InlineRenderer.installStartupScreen(...)` 挂到 `LineReader.CALLBACK_INIT`，首次进入输入时用 `printAbove` 一次性显示完整 Banner + tips，避免 logo 被 LineReader 首次重绘滚出可视区域。
- `BottomStatusBar` 现在是 JLine `Status` 托管的底部 dock：由 JLine 维护滚动区域和状态行位置，不再手写 `\n` / `moveUp` / `CLEAR_TO_EOS` 清屏。输入期会把 LineReader 光标定位到 dock 上方一行，让 `*` 输入行和 Status 同处底部区域；dock 保留两类信息：上层模式 + MCP/Skill 摘要，下层 Auto Model / model / phase / ctx 百分比与 token / cost / elapsed / cwd。
- 普通任务和斜杠命令提交后，`Main` 会把本轮原始输入以暗色整行块写回 transcript：输入态左提示仍是 `* `，提交回显左提示改为 `>`；单行输入只占一行，不额外追加空白行。普通任务随后再展开 MCP resource / 本地 `@path` 并进入 Agent；不要只依赖 JLine 提交行残留，否则 activity 重绘或 dock 刷新可能让用户输入从可见历史里消失。
- ReAct LLM 调用期间，inline renderer 使用固定高度 live thinking 区动态显示 `Thinking...` 和灰色竖线 reasoning 预览；该区域只能清理自己刚打印的几行，不能用独立 JLine `Display.update()` / `CLEAR_TO_EOS` 向上覆盖 transcript。content 或 tool call 开始前先清掉 live 区，再把完整 reasoning 引用块落到正文区，正文回答用低调标记起始，不再刷强标题。
- Phase 22 开始，`InlineRenderer` 可绑定当前 `LineReader`；当 `LineReader.isReading()` 为 true 时，`Renderer.stream()` 的完整行输出优先通过 `LineReader#printAbove` 显示在输入行上方，未绑定 / 非读取态 / 测试路径回退到原 `PrintStream`。
- ReAct 正常结束后不再把 `📊 Token: ...` 打进正文区；token/cost/elapsed 会保留在底部强状态行，phase 回到 `idle`。
- 默认 CLI 启动路径应尽早建立 `Terminal -> LineReader -> Renderer`，启动 Banner、模型加载、MCP 启动、Skill summary、ReAct 提示和退出提示都应走 `Renderer.stream()`；除 fatal bootstrap / runtime API / legacy TUI 降级外，不要在交互主路径新增裸 `System.out.println`。
- 启动期 MCP 不得阻塞首屏：CLI 默认最多等待 8 秒（`TCODE_MCP_STARTUP_WAIT_SECONDS` / `-Dtcode.mcp.startup.wait.seconds` 可调），超时后保留未完成 server 为 `STARTING` 并后台继续初始化；`/mcp` 查看最新状态。
- `LineReader` 使用 `TCodeHighlighter` 做输入实时高亮：slash 命令、`@` 引用、`@image:`、`@clipboard`、敏感词和明显危险 shell 片段会在编辑阶段被标记；不要把这类视觉提示混入最终提交文本。
- `LineReader` 使用 `TCodeCompleter` 做上下文补全：`/model` provider、`/mcp` 子命令与 server、`/skill` 子命令与 skill name、`/task` / `/browser` / `/snapshot` 子命令、`@image:` 本地路径、本地 `@path` 和 MCP resource `@server:uri` 引用都应从同一个 completer 出口维护。
- 普通用户输入进入 Agent 前会先展开 MCP resource mention，再由 `LocalPathMentionExpander` 展开本地 `@path`：文件会内联为 `<file>` 块，目录会内联为 `<directory>` 列表；绝对路径或符号链接逃逸项目根时保持原文不展开。
- `LineReader` 使用 `TCodeHistory` 持久化输入历史到 `~/.tcode/history/input.history`；如果 `tcode.history.file` / `TCODE_HISTORY_FILE` 指向目录，也会自动使用该目录下的 `input.history`，避免把目录当文件读；默认忽略空白、重复、明显密钥/Bearer、base64 图片和超长输入，用户可用 `/history clear` 清空本机输入历史。
- JLine 交互升级计划记录在 `docs/phase-22-jline-interaction-upgrade.md`。

## 关键行为约束（Agent 必读）

### Memory

- 长期记忆只通过 `/save` 或用户明确要求保存；不要自动提取事实
- 长期记忆只保存跨会话稳定事实，不保存临时指令；默认项目级作用域，跨项目通用偏好才用 global
- 长期记忆必须可审计和可删除：`/memory list` / `/memory open [project|global]` / `/memory search <关键词>` / `/memory delete <id>` / `/memory clear`
- 当前会话上下文压缩由 `ContextManager` / `ConversationHistoryCompactor` 负责；会话压缩摘要使用固定 Markdown handoff 结构（Goal / Constraints / Done / Current State / Key Decisions / Open Issues / Read Files / Modified Files / Next Steps）；长工具结果进入 active context 前会按工具名保留结构化摘要，未知工具回退头尾截断；原始 user / assistant / tool / compaction 事件追加保存到用户目录 JSONL，不自动注入 LLM；`/context` 展示消息数、估算 token、压力等级、工具摘要次数和历史压缩次数；`/compact [focus]` 可手动压缩早期会话并指定摘要关注点；`/context events` / `/context recall <keyword>` / `/context show <event_id>` 只读查看原文事件；`/context inject <event_id>` 显式、限长地把单条事件注入 active context；`MemoryManager` 只保存显式长期 Markdown 记忆

### HITL + 策略层

- 拦截顺序：HitlToolRegistry → ToolRegistry → PathGuard/CommandGuard
- 用户无法批准策略拒绝的请求
- PathGuard 强制路径限定在项目根内
- CommandGuard 是辅助黑名单，不是主防线

### Plan 审阅交互

- `Enter` 执行 / `Ctrl+O` 展开 / `ESC` 取消 / `I` 补充重规划
- 方向键不应被误判为 ESC
- 涉及改动要连 raw mode 和回退路径一起看

### 并行工具

- 三条路径都走 `executeTools()`，不手写 for-loop
- 默认最多 4 个并发，结果保持原始顺序

### Web + Browser

- 已知 URL 先 `web_fetch`，SPA/防爬墙 fallback 到 Chrome DevTools MCP
- 浏览器读取优先 `take_snapshot`，不默认 `take_screenshot`
- 公开页面不要提前切 shared 模式

### Skill

- system prompt 索引段注入三处提示词，上限 20 个 / 4KB
- `load_skill` → SkillContextBuffer → 下一轮 user message 前置注入

## 修改时的硬规则

### 1. 改行为 → 同步文档

`AGENTS.md` / `README.md`（仅状态变化时）

### 2. 改命令入口 → 联动

`Main.java` + `CliCommandParser.java` + 测试 + `README.md` + `AGENTS.md`

未识别的 `/xxx` 在 CLI 层直接报"未知命令"，不回退给 Agent。

### 3. 改 Plan 审阅交互 → 联动

`Main.java` + `PlanReviewInputParser.java` + 测试 + 手工验证

### 4. 改工具集 → 联动

`ToolRegistry.java` + Agent/PlanExecuteAgent/SubAgent 提示词 + 可能 Planner 提示词 + 文档

### 5. 改模型/接口 → 联动

对应 Client + `LlmClientFactory.java` + `.env.example` + 文档

### 5.1 改 Web/搜索 → `web/` 相关 + ToolRegistry + `.env.example` + 文档 + 测试

### 5.2 改 Memory/Context → `MemoryManager` + `MarkdownMemoryStore` + `ContextManager` + `TokenBudget` + 测试 + 文档

### 5.3 改 HITL/策略 → `policy/` + ToolRegistry + HitlToolRegistry + 提示词 + `.env.example` + 文档 + 测试

### 5.4 改 MCP → `mcp/` + ToolRegistry + HITL + AuditLog + 提示词 + 文档 + 测试

### 6. 不提交 `.env` / 真实 API Key / `target/` 产物

### 7. 保持代码可读性，不过度抽象

## 验证路径

| 场景 | 命令 |
|------|------|
| 代码搜索工具 | `mvn test -Dtest=ToolRegistryTest,ApprovalPolicyTest` |
| 命令解析 | `mvn test -Dtest=CliCommandParserTest,PlanReviewInputParserTest,MainInputNormalizationTest` |
| DAG/Plan | `mvn test -Dtest=ExecutionPlanTest` |
| Multi-Agent | `mvn test -Dtest=AgentRoleTest,AgentMessageTest,AgentOrchestratorTest` |
| TUI/终端 | `mvn test -Pphase16-smoke` |
| 常规回归 | `mvn test -Pquick` |

## 给新线程的导航

1. 先看本文件 → 2. `README.md` → 3. `Main.java` → 4. 按任务进入对应模块

| 任务类型 | 先看 |
|----------|------|
| CLI 命令 | Main.java + CliCommandParser.java |
| 规划/DAG | PlanExecuteAgent.java + Planner.java + ExecutionPlan.java |
| 工具调用 | ToolRegistry.java + Agent.java |
| 代码搜索 | ToolRegistry.java (`glob_files` / `grep_code` / `read_file`) |
| 模型/API | llm/*Client.java + LlmClientFactory.java |
| Multi-Agent | AgentOrchestrator.java + SubAgent.java |
| MCP | McpServerManager.java + McpClient.java |
| TUI/渲染 | render/Renderer.java + RendererFactory.java |

## 当前已知边界

当前未交付：容器/VM 沙箱 / MCP OAuth + sampling + server 自动重启

## 持续维护约定

形成稳定协作规则时直接补进本文件，不要只留在聊天记录里。详细实现细节补到 `docs/agents-reference.md`。
