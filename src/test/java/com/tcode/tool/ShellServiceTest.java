package com.tcode.tool;

import com.tcode.policy.PolicyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellServiceTest {

    @Test
    void rejectsEmptyCommand(@TempDir Path tempDir) {
        ShellService service = new ShellService(tempDir::toString, 1);

        assertTrue(service.execute(" ").contains("命令不能为空"));
    }

    @Test
    void rejectsBroadFilesystemScan(@TempDir Path tempDir) {
        ShellService service = new ShellService(tempDir::toString, 1);

        assertThrows(PolicyException.class,
                () -> service.execute("find / -name \"pom.xml\" -type f | head -20"));
    }
}
