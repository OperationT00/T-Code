package com.tcode.context;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class JsonlContextEventStore implements ContextEventStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path path;

    public JsonlContextEventStore(Path path) {
        this.path = path;
    }

    @Override
    public synchronized void append(ContextEvent event) {
        if (event == null || path == null) {
            return;
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = MAPPER.writeValueAsString(event) + System.lineSeparator();
            Files.writeString(path, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<ContextEvent> search(String keyword, int limit) {
        String needle = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        if (needle.isBlank()) {
            return List.of();
        }
        List<ContextEvent> matches = new ArrayList<>();
        for (ContextEvent event : readAll()) {
            if (matches(event, needle)) {
                matches.add(event);
                if (matches.size() >= Math.max(1, limit)) {
                    break;
                }
            }
        }
        return matches;
    }

    @Override
    public Optional<ContextEvent> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return readAll().stream()
                .filter(event -> id.equals(event.id()))
                .findFirst();
    }

    @Override
    public List<ContextEvent> recent(int limit) {
        List<ContextEvent> events = readAll();
        int max = Math.max(1, limit);
        if (events.size() <= max) {
            return events;
        }
        return events.subList(events.size() - max, events.size());
    }

    private boolean matches(ContextEvent event, String needle) {
        return contains(event.id(), needle)
                || contains(event.turnId(), needle)
                || contains(event.role(), needle)
                || contains(event.toolName(), needle)
                || contains(event.content(), needle)
                || event.metadata().entrySet().stream()
                .anyMatch(entry -> contains(entry.getKey(), needle) || contains(entry.getValue(), needle));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private synchronized List<ContextEvent> readAll() {
        if (path == null || !Files.exists(path)) {
            return List.of();
        }
        try {
            List<ContextEvent> events = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    events.add(MAPPER.readValue(line, ContextEvent.class));
                }
            }
            return events;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
