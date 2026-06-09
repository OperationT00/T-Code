package com.tcode.context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonlContextEventStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void appendsAndSearchesEvents() {
        JsonlContextEventStore store = new JsonlContextEventStore(tempDir.resolve("events.jsonl"));
        store.append(ContextEvent.tool("turn-1", "read_file", "full content", Map.of("file", "Agent.java")));

        List<ContextEvent> results = store.search("full", 10);

        assertEquals(1, results.size());
        assertEquals("read_file", results.get(0).toolName());
        assertEquals("full content", results.get(0).content());
    }

    @Test
    void findsByIdAndReturnsRecentEventsNewestLast() {
        JsonlContextEventStore store = new JsonlContextEventStore(tempDir.resolve("events.jsonl"));
        ContextEvent first = ContextEvent.user("turn-1", "first question", Map.of());
        ContextEvent second = ContextEvent.assistant("turn-1", "second answer", Map.of());
        store.append(first);
        store.append(second);

        assertEquals(first.id(), store.findById(first.id()).orElseThrow().id());
        List<ContextEvent> recent = store.recent(10);

        assertEquals(2, recent.size());
        assertEquals(first.id(), recent.get(0).id());
        assertEquals(second.id(), recent.get(1).id());
    }

    @Test
    void searchMatchesMetadata() {
        JsonlContextEventStore store = new JsonlContextEventStore(tempDir.resolve("events.jsonl"));
        store.append(ContextEvent.tool("turn-1", "read_file", "content", Map.of("file", "Agent.java")));

        List<ContextEvent> results = store.search("agent.java", 10);

        assertEquals(1, results.size());
        assertTrue(results.get(0).metadata().containsValue("Agent.java"));
    }
}
