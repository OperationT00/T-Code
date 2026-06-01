package com.tcode.tool;

import com.tcode.browser.BrowserConnector;
import com.tcode.skill.SkillContextBuffer;
import com.tcode.skill.SkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ToolRuntimeBindingsTest {

    @Test
    void storesMutableProviderDependencies() {
        ToolRuntimeBindings bindings = new ToolRuntimeBindings();
        BrowserConnector connector = new StubBrowserConnector();
        SkillRegistry registry = new SkillRegistry(null, null, null, null);
        SkillContextBuffer buffer = new SkillContextBuffer();
        AtomicReference<String> saved = new AtomicReference<>();

        bindings.setBrowserConnector(connector);
        bindings.setMemorySaver((fact, scope) -> saved.set(scope + ":" + fact));
        bindings.setSkillRegistry(registry);
        bindings.setSkillContextBuffer(buffer);

        bindings.memorySaver().accept("concise", "global");
        assertSame(connector, bindings.browserConnector());
        assertEquals("global:concise", saved.get());
        assertSame(registry, bindings.skillRegistry());
        assertSame(buffer, bindings.skillContextBuffer());
    }

    private static final class StubBrowserConnector implements BrowserConnector {
        @Override public String connectDefault() { return "connected"; }
        @Override public String disconnect() { return "disconnected"; }
        @Override public String status() { return "ready"; }
    }
}
