package com.tcode.cli;

import com.tcode.mcp.McpServerManager;
import com.tcode.mcp.mention.AtMentionExpander;

import java.io.PrintStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

record CliMcpInfrastructure(
        AtMentionExpander mentionExpander,
        LocalPathMentionExpander localPathMentionExpander,
        String startupNote
) {
    private static final String DEFAULT_CHROME_DEVTOOLS_MCP_JSON = """
            {
              "mcpServers": {
                "chrome-devtools": {
                  "command": "npx",
                  "args": ["-y", "chrome-devtools-mcp@latest", "--isolated=true"]
                }
              }
            }
            """;

    static CliMcpInfrastructure start(Path home,
                                      Path projectDir,
                                      McpServerManager mcpServerManager,
                                      PrintStream ui,
                                      Duration startupWait,
                                      Consumer<Thread> shutdownHookRegistrar) {
        String startupNote = "";
        try {
            McpConfigBootstrapResult bootstrapResult = ensureDefaultMcpConfig(home);
            startupNote = bootstrapResult.message();
            mcpServerManager.loadConfiguredServers();
            mcpServerManager.startAll(ui, startupWait);
            shutdownHookRegistrar.accept(new Thread(mcpServerManager::close, "tcode-mcp-shutdown"));
        } catch (Exception e) {
            startupNote = "MCP 初始化失败: " + e.getMessage();
        }
        return new CliMcpInfrastructure(
                new AtMentionExpander(mcpServerManager),
                new LocalPathMentionExpander(projectDir),
                startupNote
        );
    }

    static McpConfigBootstrapResult ensureDefaultMcpConfig(Path userHome) throws IOException {
        Path configFile = userHome.resolve(".tcode").resolve("mcp.json");
        if (Files.notExists(configFile)) {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, DEFAULT_CHROME_DEVTOOLS_MCP_JSON);
            return new McpConfigBootstrapResult(true,
                    "✅ 已创建默认 MCP 配置: " + configFile
                            + "\n   默认启用 chrome-devtools（isolated 模式）。");
        }
        String content = Files.readString(configFile);
        if (!content.contains("\"chrome-devtools\"")) {
            return new McpConfigBootstrapResult(false,
                    "ℹ️ 检测到 ~/.tcode/mcp.json 未配置 chrome-devtools，建议参考 README 添加浏览器 MCP server。");
        }
        return new McpConfigBootstrapResult(false, "");
    }

    record McpConfigBootstrapResult(boolean created, String message) {
    }
}
