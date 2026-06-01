package com.tcode.cli;

import com.tcode.browser.BrowserConnectivityCheck;
import com.tcode.browser.BrowserConnector;
import com.tcode.browser.BrowserGuard;
import com.tcode.browser.BrowserSession;
import com.tcode.browser.SensitivePagePolicy;
import com.tcode.hitl.HitlToolRegistry;
import com.tcode.hitl.SwitchableHitlHandler;
import com.tcode.hitl.TerminalHitlHandler;
import com.tcode.mcp.McpServerManager;

import java.nio.file.Path;

record CliSessionInfrastructure(
        TerminalHitlHandler terminalHitlHandler,
        SwitchableHitlHandler hitlHandler,
        HitlToolRegistry hitlToolRegistry,
        BrowserSession browserSession,
        BrowserConnectivityCheck browserConnectivityCheck,
        McpServerManager mcpServerManager
) {

    static CliSessionInfrastructure create(Path projectDir) {
        TerminalHitlHandler terminalHitlHandler = new TerminalHitlHandler(false);
        SwitchableHitlHandler hitlHandler = new SwitchableHitlHandler(terminalHitlHandler);
        HitlToolRegistry hitlToolRegistry = new HitlToolRegistry(hitlHandler);
        BrowserSession browserSession = new BrowserSession();
        BrowserConnectivityCheck browserConnectivityCheck = new BrowserConnectivityCheck();
        hitlToolRegistry.setBrowserGuard(new BrowserGuard(browserSession, new SensitivePagePolicy()));
        McpServerManager mcpServerManager = new McpServerManager(hitlToolRegistry, projectDir);
        hitlToolRegistry.setBrowserConnector(new BrowserConnector() {
            @Override
            public String status() {
                return CliBrowserCommandHandler.handle("status", browserSession, browserConnectivityCheck,
                        mcpServerManager, hitlToolRegistry, hitlHandler);
            }

            @Override
            public String connectDefault() {
                return CliBrowserCommandHandler.handle("connect", browserSession, browserConnectivityCheck,
                        mcpServerManager, hitlToolRegistry, hitlHandler);
            }

            @Override
            public String disconnect() {
                return CliBrowserCommandHandler.handle("disconnect", browserSession, browserConnectivityCheck,
                        mcpServerManager, hitlToolRegistry, hitlHandler);
            }
        });
        return new CliSessionInfrastructure(
                terminalHitlHandler,
                hitlHandler,
                hitlToolRegistry,
                browserSession,
                browserConnectivityCheck,
                mcpServerManager
        );
    }
}
