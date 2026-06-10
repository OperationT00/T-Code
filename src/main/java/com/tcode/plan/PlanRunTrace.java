package com.tcode.plan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlanRunTrace {
    private final List<Event> events = new ArrayList<>();

    public synchronized void record(String type, String taskId, Map<String, String> attributes) {
        events.add(new Event(Instant.now().toString(), type, taskId,
                attributes == null ? Map.of() : Map.copyOf(attributes)));
    }

    public synchronized List<Event> events() {
        return List.copyOf(events);
    }

    public record Event(String timestamp, String type, String taskId, Map<String, String> attributes) {
    }
}
