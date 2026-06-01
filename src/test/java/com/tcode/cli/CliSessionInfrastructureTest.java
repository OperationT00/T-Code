package com.tcode.cli;

import com.tcode.browser.BrowserMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliSessionInfrastructureTest {

    @Test
    void createsConnectedHitlBrowserAndMcpInfrastructure(@TempDir Path projectDir) {
        CliSessionInfrastructure infrastructure = CliSessionInfrastructure.create(projectDir);

        assertSame(infrastructure.terminalHitlHandler(), infrastructure.hitlHandler().getDelegate());
        assertSame(infrastructure.hitlHandler(), infrastructure.hitlToolRegistry().getHitlHandler());

        infrastructure.browserSession().switchToShared("http://127.0.0.1:9222");
        String result = infrastructure.hitlToolRegistry().executeTool("browser_disconnect", "{}");

        assertTrue(result.contains("chrome-devtools"));
        assertEquals(BrowserMode.ISOLATED, infrastructure.browserSession().mode());
    }
}
