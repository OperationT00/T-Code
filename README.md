# t-code

一个使用 Java 构建的 Agent Coding CLI。

`t-code` 可以在终端中理解代码库、读取和修改文件、执行命令、规划复杂任务，并通过 Memory、RAG、联网搜索、MCP 与 Skill 扩展能力。

当前公开版本：`v1.0.0`

## 功能亮点

- **三种执行模式**：默认 ReAct、`/plan` 规划执行、`/team` 多 Agent 协作
- **代码库探索**：支持 glob、grep、文件读取和 RAG 语义检索
- **上下文与记忆**：短期上下文压缩、长期记忆、项目级与全局偏好
- **工具能力**：文件操作、Shell、联网搜索、网页抓取和并行工具调用
- **MCP 与 Skill**：接入 stdio、Streamable HTTP server 和按需加载的 Skill
- **终端体验**：JLine inline TUI、状态栏、输入补全、历史记录、图片输入和任务取消
- **安全边界**：项目路径围栏、命令策略、HITL 审批、审计日志和 Side-Git 快照
- **多模型适配**：支持 GLM、DeepSeek、StepFun 与 Kimi

## 快速开始

### 环境要求

- Java 17+
- Maven
- 至少一个模型 API Key

### Linux / macOS

```bash
git clone https://github.com/itwanger/tcode.git
cd tcode
cp .env.example .env
${EDITOR:-nano} .env
mvn clean package
java -jar target/t-code-1.0-SNAPSHOT.jar
```

### Windows PowerShell

```powershell
git clone https://github.com/itwanger/tcode.git
cd tcode
Copy-Item .env.example .env
notepad .env
mvn clean package
java -jar target/t-code-1.0-SNAPSHOT.jar
```

在 `.env` 中填写至少一个模型 API Key：

```bash
GLM_API_KEY=
DEEPSEEK_API_KEY=
STEP_API_KEY=
KIMI_API_KEY=
```

启动后可以直接输入任务：

```text
分析当前项目结构，并告诉我最值得优先阅读的文件
```

复杂任务可以使用规划模式：

```text
/plan 为这个项目增加一个健康检查接口，并在完成后运行测试
```

## 执行模式

| 模式 | 入口 | 适用场景 |
|---|---|---|
| ReAct | 默认模式 | 日常问答、代码探索、单步修改 |
| Plan-and-Execute | `/plan <任务>` | 多步骤任务、存在依赖关系的改动 |
| Multi-Agent | `/team <任务>` | 需要规划、执行与审查协作的复杂任务 |

## 常用命令

| 命令 | 用途 |
|---|---|
| `/plan <任务>` | 使用 Plan-and-Execute 执行任务 |
| `/team <任务>` | 使用 Multi-Agent 执行任务 |
| `/cancel` | 取消当前任务 |
| `/clear` | 清空当前对话 |
| `/context` | 查看上下文与 Token 状态 |
| `/index [路径]` | 建立代码索引 |
| `/search <查询>` | 语义检索代码 |
| `/memory list` | 查看长期记忆 |
| `/mcp` | 查看 MCP server 状态 |
| `/browser status` | 查看浏览器连接状态 |
| `/hitl on` | 开启危险操作审批 |
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

## Runtime API

Java Runtime 可以作为 HTTP 服务运行，为独立客户端提供 thread、turn、events 和 HITL approval 接口：

```powershell
$env:TCODE_RUNTIME_API_KEY='local-secret'
java -jar target/t-code-1.0-SNAPSHOT.jar serve --http --port 8080
```

仓库中的 [TypeScript CLI](clients/t-code-cli/README.md) 是 Runtime API 的实验性薄客户端。

## 开发验证

```bash
mvn test -Pquick
mvn test -Pphase16-smoke
mvn test -DskipTests=false
```

## 文档

- [CHANGELOG.md](CHANGELOG.md)：公开版本记录
- [docs/journey/](docs/journey/)：从 `v0.1.0` 到 `v1.0.0` 的开发历程
- [AGENTS.md](AGENTS.md)：面向开发者与 Agent 的协作指南
- [LICENSE](LICENSE)：MIT 开源许可证
