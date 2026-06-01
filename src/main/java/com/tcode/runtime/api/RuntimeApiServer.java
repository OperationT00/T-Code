package com.tcode.runtime.api;

import com.tcode.runtime.task.TaskRunner;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class RuntimeApiServer implements AutoCloseable {
    private final RuntimeApiAuthPolicy authPolicy;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "tcode-runtime-api");
        thread.setDaemon(true);
        return thread;
    });

    public RuntimeApiServer(RuntimeThreadStore store, TaskRunner runner, int port, String apiKey) throws IOException {
        this(store, adapt(runner), null, port, apiKey);
    }

    public RuntimeApiServer(RuntimeThreadStore store, RuntimeTurnRunner runner, int port, String apiKey) throws IOException {
        this(store, runner, null, port, apiKey);
    }

    public RuntimeApiServer(RuntimeThreadStore store, TaskRunner runner, RuntimeHitlHandler hitlHandler,
                            int port, String apiKey) throws IOException {
        this(store, adapt(runner), hitlHandler, port, apiKey);
    }

    public RuntimeApiServer(RuntimeThreadStore store, RuntimeTurnRunner runner, RuntimeHitlHandler hitlHandler,
                            int port, String apiKey) throws IOException {
        this.authPolicy = new RuntimeApiAuthPolicy(apiKey);
        RuntimeThreadRoutes threadRoutes = new RuntimeThreadRoutes(store, runner, executor);
        RuntimeApprovalRoutes approvalRoutes = new RuntimeApprovalRoutes(hitlHandler);
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.server.createContext("/v1/threads", exchange -> handle(exchange, threadRoutes::handle));
        this.server.createContext("/v1/approvals", exchange -> handle(exchange, approvalRoutes::handle));
        this.server.setExecutor(executor);
    }

    private void handle(HttpExchange exchange, Function<RuntimeApiRequest, RuntimeApiResult> routes) throws IOException {
        if (!authorized(exchange)) {
            write(exchange, RuntimeApiResult.json(401, RuntimeApiResponses.error("unauthorized")));
            return;
        }
        RuntimeApiRequest request = new RuntimeApiRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getQuery(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        write(exchange, routes.apply(request));
    }

    private boolean authorized(HttpExchange exchange) {
        return authPolicy.authorized(Map.of(
                "Authorization", header(exchange, "Authorization"),
                RuntimeApiAuthPolicy.API_KEY_HEADER,
                header(exchange, RuntimeApiAuthPolicy.API_KEY_HEADER)));
    }

    private static String header(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        return value == null ? "" : value;
    }

    private static RuntimeTurnRunner adapt(TaskRunner runner) {
        if (runner == null) {
            return (input, events) -> "";
        }
        return (input, events) -> runner.run(input);
    }

    public static String configuredApiKey() {
        return RuntimeApiAuthPolicy.configuredApiKey();
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private static void write(HttpExchange exchange, RuntimeApiResult result) throws IOException {
        byte[] bytes = result.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", result.contentType());
        exchange.sendResponseHeaders(result.status(), bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
