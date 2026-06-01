package com.tcode.cli;

import com.tcode.config.TCodeConfig;
import com.tcode.llm.LlmClient;
import com.tcode.llm.LlmClientFactory;
import com.tcode.runtime.CoreRuntime;
import com.tcode.runtime.api.RuntimeApiServer;
import com.tcode.runtime.api.RuntimeHitlHandler;
import com.tcode.runtime.api.RuntimeThreadStore;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

final class CliRuntimeApiLauncher {
    private CliRuntimeApiLauncher() {
    }

    static boolean isServeCommand(String[] args) {
        return args != null
                && args.length >= 1
                && "serve".equalsIgnoreCase(args[0])
                && Arrays.stream(args).anyMatch("--http"::equalsIgnoreCase);
    }

    static void startAndBlock(String[] args) {
        TCodeConfig config = TCodeConfig.load();
        LlmClient client = LlmClientFactory.createFromConfig(config);
        if (client == null) {
            System.err.println("❌ 错误: 未找到可用的 API Key");
            System.exit(1);
        }
        int port = parsePort(args, 8080);
        try {
            RuntimeThreadStore store = new RuntimeThreadStore(RuntimeThreadStore.defaultDbPath());
            RuntimeHitlHandler runtimeHitl = new RuntimeHitlHandler();
            CoreRuntime runtime = CoreRuntime.headless(client, Path.of("."), runtimeHitl);
            RuntimeApiServer server = new RuntimeApiServer(
                    store,
                    runtime.turnRunner(),
                    runtimeHitl,
                    port,
                    RuntimeApiServer.configuredApiKey());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.close();
                store.close();
            }, "tcode-runtime-api-shutdown"));
            server.start();
            System.out.println("✅ t-code Runtime API 已启动: http://127.0.0.1:" + server.port());
            System.out.println("   认证: Authorization: Bearer <TCODE_RUNTIME_API_KEY>");
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("❌ Runtime API 启动失败: " + e.getMessage());
            System.exit(1);
        }
    }

    static int parsePort(String[] args, int defaultPort) {
        if (args == null) {
            return defaultPort;
        }
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equalsIgnoreCase(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException ignored) {
                    return defaultPort;
                }
            }
        }
        return defaultPort;
    }

    static String runHeadlessTask(String prompt, LlmClient llmClient) {
        try {
            return CoreRuntime.headless(llmClient, Path.of(".")).turnRunner().run(prompt, null);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
