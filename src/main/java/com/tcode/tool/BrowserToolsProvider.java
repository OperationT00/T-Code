package com.tcode.tool;

import com.tcode.browser.BrowserConnector;

import java.util.function.Supplier;

public final class BrowserToolsProvider implements ToolProvider {
    private final Supplier<BrowserConnector> connectorSupplier;

    public BrowserToolsProvider(Supplier<BrowserConnector> connectorSupplier) {
        this.connectorSupplier = connectorSupplier == null ? () -> null : connectorSupplier;
    }

    @Override
    public void register(ToolRegistrationContext context) {
        context.register(
                "browser_connect",
                "Connect to an existing browser session when a page requires logged-in state.",
                context.parameters(),
                args -> connector() == null
                        ? "browser connector is not initialized"
                        : connector().connectDefault()
        );
        context.register(
                "browser_disconnect",
                "Disconnect from the shared browser session and return to isolated mode.",
                context.parameters(),
                args -> connector() == null
                        ? "browser connector is not initialized"
                        : connector().disconnect()
        );
        context.register(
                "browser_status",
                "Show current browser MCP mode and connection status.",
                context.parameters(),
                args -> connector() == null
                        ? "browser connector is not initialized"
                        : connector().status()
        );
    }

    private BrowserConnector connector() {
        return connectorSupplier.get();
    }
}
