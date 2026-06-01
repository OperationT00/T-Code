package com.tcode.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RagSearchServiceTest {

    @Test
    void asksForIndexWhenProjectHasNoIndexedChunks(@TempDir Path tempDir) {
        RagSearchService service = new RagSearchService(tempDir::toString);

        assertTrue(service.search("memory manager", 5).contains("/index"));
    }
}
