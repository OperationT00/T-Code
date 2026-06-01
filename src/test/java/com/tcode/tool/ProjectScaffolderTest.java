package com.tcode.tool;

import com.tcode.policy.PathGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectScaffolderTest {

    @Test
    void createsNodeProjectInsideGuardedRoot(@TempDir Path tempDir) {
        ProjectScaffolder scaffolder = new ProjectScaffolder(() -> new PathGuard(tempDir.toString()));

        String result = scaffolder.create(Map.of("name", "demo", "type", "node"));

        assertTrue(result.contains("项目已创建: demo"));
        assertTrue(Files.exists(tempDir.resolve("demo/package.json")));
    }
}
