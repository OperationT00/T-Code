package com.tcode.tool;

import com.tcode.policy.PathGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSearchServiceTest {

    @Test
    void globsRootFilesAndSkipsDependencyDirectoriesWhenGrepping(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve("node_modules/pkg"));
        Files.writeString(tempDir.resolve("README.md"), "# demo\n");
        Files.writeString(tempDir.resolve("src/App.java"), "class App { String marker = \"targetSymbol\"; }\n");
        Files.writeString(tempDir.resolve("node_modules/pkg/Generated.java"),
                "class Generated { String marker = \"targetSymbol\"; }\n");
        FileSearchService service = new FileSearchService(() -> new PathGuard(tempDir.toString()));

        String glob = service.glob(Map.of("pattern", "README.md"));
        String grep = service.grep(Map.of("pattern", "targetSymbol"));

        assertTrue(glob.contains("README.md"));
        assertTrue(grep.contains("App.java:1"));
        assertFalse(grep.contains("node_modules"));
    }
}
