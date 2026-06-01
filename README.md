# t-code

一个使用 Java 构建的 Agent Coding CLI，对标 Claude Code。

`t-code` 可以在终端中理解代码库、读取和修改文件、执行命令、规划复杂任务，并通过 Memory、RAG、联网搜索、MCP 与 Skill 扩展能力。项目从一个可运行的 ReAct Agent 开始，逐步演进为面向真实代码仓库工作的终端工具。

当前公开版本：`v1.0.0`

## 功能亮点

- **三种执行模式**：默认 ReAct、`/plan` 规划执行、`/team` 多 Agent 协作
- **代码库探索**：支持 glob、grep、文件读取和 RAG 语义检索
- **记忆系统**：短期上下文压缩、可审计的长期记忆、项目级与全局偏好
- **并行工具调用**：同一轮独立工具可以并发执行，结果保持原始顺序
- **联网能力**：提供 `web_search` 与 `web_fetch`，动态页面可交给浏览器 MCP
- **MCP 支持**：接入 stdio 与 Streamable HTTP server，支持 tools、resources 和 prompts
- **Skill 系统**：按需加载专家手册，避免把所有规则一次性塞入 system prompt
- **终端体验**：JLine inline TUI、状态栏、输入补全、历史记录、图片输入和任务取消
- **安全边界**：项目路径围栏、命令策略、HITL 审批、审计日志和 Side-Git 快照
- **多模型适配**：支持 GLM、DeepSeek、StepFun 与 Kimi

## 快速开始

### 环境要求

- Java 17+
- Maven
- 至少一个模型 API Key

### 配置

```bash
cp .env.example .env
```

在 `.env` 中填写至少一个 API Key：

```bash
GLM_API_KEY=
DEEPSEEK_API_KEY=
STEP_API_KEY=
KIMI_API_KEY=
```

### 编译运行

```bash
mvn clean package
java -jar target/t-code-1.0-SNAPSHOT.jar
```

启动后，可以直接输入任务：

```text
分析当前项目结构，并告诉我最值得优先阅读的文件
```

也可以使用规划模式：

```text
/plan 为这个项目增加一个健康检查接口，并在完成后运行测试
```

## 执行模式

| 模式 | 入口 | 适用场景 |
|---|---|---|
| ReAct | 默认模式 | 日常问答、代码探索、单步修改 |
| Plan-and-Execute | `/plan <任务>` | 多步骤任务、存在依赖关系的改动 |
| Multi-Agent | `/team <任务>` | 需要规划、执行与审查协作的复杂任务 |

Plan 模式生成计划后会等待确认：

- `Enter`：执行当前计划
- `Ctrl+O`：展开完整计划
- `ESC`：折叠或取消
- `I`：补充要求并重新规划

## 内置工具

| 工具 | 用途 |
|---|---|
| `read_file` | 读取项目内文件 |
| `write_file` | 写入项目内文件 |
| `list_dir` | 查看目录内容 |
| `glob_files` | 按文件名查找文件 |
| `grep_code` | 按关键词或正则搜索代码 |
| `execute_command` | 执行短时 Shell 命令 |
| `create_project` | 创建 Java、Python 或 Node 项目结构 |
| `search_code` | RAG 语义检索 |
| `web_search` | 搜索互联网 |
| `web_fetch` | 抓取 URL 并提取正文 |
| `revert_turn` | 恢复最近的 Side-Git 快照 |

MCP server 提供的工具会动态注册为：

```text
mcp__{server}__{tool}
```

精确定位代码时，优先使用 `glob_files`、`grep_code` 和 `read_file`。`search_code` 更适合描述模糊、关键词不明确或跨文件理解的场景。

## 常用命令

### 任务与会话

| 命令 | 用途 |
|---|---|
| `/plan <任务>` | 使用 Plan-and-Execute 执行任务 |
| `/team <任务>` | 使用 Multi-Agent 执行任务 |
| `/cancel` | 取消当前任务 |
| `/clear` | 清空当前对话 |
| `/context` | 查看上下文模式与 Token 状态 |
| `/exit` | 退出 |

### Memory 与代码检索

| 命令 | 用途 |
|---|---|
| `/save <事实>` | 保存项目级长期记忆 |
| `/save --global <事实>` | 保存全局偏好 |
| `/memory list` | 查看长期记忆 |
| `/memory search <关键词>` | 搜索长期记忆 |
| `/memory delete <id>` | 删除一条记忆 |
| `/index [路径]` | 建立代码索引 |
| `/search <查询>` | 语义检索代码 |
| `/graph <类名>` | 查看代码关系图谱 |

### MCP、浏览器与安全

| 命令 | 用途 |
|---|---|
| `/mcp` | 查看 MCP server 状态 |
| `/mcp restart <name>` | 重启 MCP server |
| `/mcp resources <name>` | 查看 MCP resources |
| `/mcp prompts <name>` | 查看 MCP prompts |
| `/browser status` | 查看浏览器连接状态 |
| `/browser connect` | 切换到共享浏览器会话 |
| `/browser tabs` | 查看浏览器标签页 |
| `/hitl on` | 开启危险操作审批 |
| `/policy` | 查看安全策略 |
| `/audit [N]` | 查看最近审计记录 |
| `/snapshot` | 查看 Side-Git 快照 |
| `/restore <N>` | 恢复快照 |

输入 `/` 后可以使用 Tab 补全命令。

## MCP 配置

MCP 默认开启。首次运行时，如果 `~/.tcode/mcp.json` 不存在，程序会自动创建 Chrome DevTools MCP 配置。

也可以手动配置用户级 `~/.tcode/mcp.json` 或项目级 `.tcode/mcp.json`：

```json
{
  "mcpServers": {
    "fetch": {
      "command": "uvx",
      "args": ["mcp-server-fetch"]
    },
    "remote-demo": {
      "url": "https://mcp.example.com/v1",
      "headers": {
        "Authorization": "Bearer ${REMOTE_TOKEN}"
      }
    }
  }
}
```

- `command`：启动 stdio server
- `url`：连接 Streamable HTTP server
- `${PROJECT_DIR}` 与 `${HOME}`：内置变量
- 其他 `${VAR}`：从环境变量读取

## Runtime API

Java Runtime 可以作为 HTTP 服务运行，为独立客户端提供 thread、turn、events 和 HITL approval 接口：

```powershell
$env:TCODE_RUNTIME_API_KEY='local-secret'
java -jar target/t-code-1.0-SNAPSHOT.jar serve --http --port 8080
```

仓库中提供了一个 TypeScript CLI 示例：

```powershell
$env:TCODE_RUNTIME_API_KEY='local-secret'
node --experimental-strip-types clients/t-code-cli/src/index.ts
```

## 版本演进

| 版本 | 主题 |
|---|---|
| `v0.1.0` | 三路执行模式：ReAct、Plan-and-Execute、Multi-Agent |
| `v0.2.0` | Memory 与上下文工程 |
| `v0.3.0` | RAG 与代码库理解 |
| `v0.4.0` | 并行工具执行 |
| `v0.5.0` | 联网搜索与网页抓取 |
| `v0.6.0` | MCP tools、resources 与 prompts |
| `v0.7.0` | Skill 系统 |
| `v1.0.0` | TUI、LSP、快照、Runtime API |

完整开发历程见 [CHANGELOG.md](CHANGELOG.md) 与 [docs/journey/](docs/journey/)。

## 项目结构

```text
src/main/java/com/tcode/
├── agent/       ReAct、Plan-and-Execute 与 Multi-Agent
├── browser/     浏览器会话与敏感页面策略
├── cli/         命令行入口与交互组件
├── context/     上下文模式与 Token 展示
├── hitl/        危险操作审批
├── image/       图片输入
├── llm/         模型客户端
├── lsp/         LSP 诊断
├── mcp/         MCP 客户端、传输层与 resources
├── memory/      短期与长期记忆
├── plan/        规划器与任务 DAG
├── policy/      路径、命令与审计策略
├── prompt/      Prompt 分层
├── rag/         代码索引、向量检索与关系图谱
├── render/      inline、plain 与 lanterna renderer
├── runtime/     Core Runtime 与 HTTP API
├── skill/       Skill 注册与上下文注入
├── snapshot/    Side-Git 快照
├── tool/        工具注册与执行管线
└── web/         搜索、抓取与正文提取
```

## 技术栈

- Java 17
- Maven
- JLine 4
- OkHttp
- Jackson
- SQLite
- JavaParser
- JGit
- Jsoup

## 开发验证

```bash
# 常规回归
mvn test -Pquick

# TUI 冒烟测试
mvn test -Pphase16-smoke

# 全量测试
mvn test -DskipTests=false
```

## 文档

- [CHANGELOG.md](CHANGELOG.md)：公开版本记录
- [docs/journey/](docs/journey/)：从 `v0.1.0` 到 `v1.0.0` 的开发历程
- [ROADMAP.md](ROADMAP.md)：详细阶段路线
- [AGENTS.md](AGENTS.md)：面向开发者与 Agent 的协作指南
