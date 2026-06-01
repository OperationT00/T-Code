package com.tcode.tool;

import com.tcode.policy.PathGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileServiceTest {

    @Test
    void writesReadsAndListsInsideGuardedProject(@TempDir Path tempDir) throws Exception {
        List<String> edits = new ArrayList<>();
        FileService service = new FileService(
                () -> new PathGuard(tempDir.toString()),
                () -> (path, beforeAfter) -> edits.add(path + ":" + beforeAfter[1]),
                (path, safe) -> edits.add("lsp:" + path)
        );

        assertEquals("文件已写入: notes/demo.txt",
                service.write(Map.of("path", "notes/demo.txt", "content", "hello")));
        assertTrue(service.read(Map.of("path", "notes/demo.txt")).contains("hello"));
        assertTrue(service.list(Map.of("path", "notes")).contains("[F] demo.txt"));
        assertEquals("hello", Files.readString(tempDir.resolve("notes/demo.txt")));
        assertEquals(List.of("notes/demo.txt:hello", "lsp:notes/demo.txt"), edits);
    }
}
