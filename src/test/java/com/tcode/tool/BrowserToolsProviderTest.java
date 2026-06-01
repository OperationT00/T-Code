package com.tcode.tool;

import com.tcode.browser.BrowserConnector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserToolsProviderTest {

    @Test
    void registersBrowserToolsWithInjectedConnector() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new BrowserToolsProvider(() -> new BrowserConnector() {
            @Override
            public String status() {
                return "status-ok";
            }

            @Override
            public String connectDefault() {
                return "connected";
            }

            @Override
            public String disconnect() {
                return "disconnected";
            }
        }));

        assertTrue(registry.hasTool("browser_connect"));
        assertEquals("connected", registry.executeTool("browser_connect", "{}"));
        assertEquals("status-ok", registry.executeTool("browser_status", "{}"));
        assertEquals("disconnected", registry.executeTool("browser_disconnect", "{}"));
    }
}
