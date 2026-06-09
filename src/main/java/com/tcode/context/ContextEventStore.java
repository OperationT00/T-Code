package com.tcode.context;

import java.util.List;
import java.util.Optional;

public interface ContextEventStore {
    void append(ContextEvent event);

    List<ContextEvent> search(String keyword, int limit);

    Optional<ContextEvent> findById(String id);

    List<ContextEvent> recent(int limit);

    static ContextEventStore noop() {
        return NoopContextEventStore.INSTANCE;
    }

    final class NoopContextEventStore implements ContextEventStore {
        private static final NoopContextEventStore INSTANCE = new NoopContextEventStore();

        private NoopContextEventStore() {
        }

        @Override
        public void append(ContextEvent event) {
        }

        @Override
        public List<ContextEvent> search(String keyword, int limit) {
            return List.of();
        }

        @Override
        public Optional<ContextEvent> findById(String id) {
            return Optional.empty();
        }

        @Override
        public List<ContextEvent> recent(int limit) {
            return List.of();
        }
    }
}
