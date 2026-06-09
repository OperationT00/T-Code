# t-code Changelog

公开版本以能力跃迁为主线。内部设计细节保留在 `docs/phase-*` 文档中。

## v1.0.2 - Context and memory simplification

- Removed RAG from the core coding-agent path and kept realtime code exploration as the default.
- Reworked context management with pressure levels, structured tool summaries, structured conversation compaction, JSONL raw context events, and explicit recall/inject commands.
- Simplified long-term memory to explicit Markdown-backed project/global memory.

## v1.0.0 - 产品化发布

- 加入 inline TUI、LSP 诊断、Side-Git 快照、Prompt 分层、Runtime API 和图片输入。
- 将 Java 保留为 Core Runtime，为独立客户端提供稳定边界。
- 统一公开品牌为 `t-code`，开屏使用大写 `T` logo。
- 全仓命名空间统一为 `tcode`；本次公开发布不保留旧目录、旧配置键或旧 Header 的兼容读取。

## v0.7.0 - Skill

- 支持内置、用户级和项目级 Skill。
- Skill 按需加载，避免把所有专家知识一次性塞入 system prompt。

## v0.6.0 - MCP

- 支持 stdio 与 Streamable HTTP MCP server。
- 动态注册工具、resources 和 prompts，并接入 Chrome DevTools。

## v0.5.0 - 联网搜索

- 加入 `web_search` 与 `web_fetch`。
- 增加网络策略和浏览器 fallback。

## v0.4.0 - 并行执行

- 同一轮独立工具调用并行执行。
- Plan DAG 与 Multi-Agent 支持受控并发，结果保持原始顺序。

## v0.3.0 - RAG

- 加入代码切片、Embedding、向量存储和语义检索。
- RAG 作为模糊检索辅助，不替代实时文件搜索。

## v0.2.0 - Memory

- 区分短期上下文压缩与长期记忆。
- 长期记忆仅在用户明确要求时保存，并支持审计和删除。

## v0.1.0 - 三路执行模式

- ReAct 负责默认探索与执行。
- Plan-and-Execute 负责可审阅计划与 DAG。
- Multi-Agent 负责复杂任务协作。

详细历程见 [docs/journey/](docs/journey/)。
