package com.tcode.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileToolsProviderTest {

    @Test
    void delegatesReadWriteAndListArguments() {
        List<Map<String, String>> calls = new ArrayList<>();
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new FileToolsProvider(
                args -> record(calls, args, "read-ok"),
                args -> record(calls, args, "write-ok"),
                args -> record(calls, args, "list-ok")
        ));

        assertEquals("read-ok", registry.executeTool("read_file", "{\"path\":\"a.txt\"}"));
        assertEquals("write-ok", registry.executeTool("write_file", "{\"path\":\"a.txt\",\"content\":\"hi\"}"));
        assertEquals("list-ok", registry.executeTool("list_dir", "{\"path\":\".\"}"));
        assertEquals(List.of("a.txt", "a.txt", "."), calls.stream().map(args -> args.get("path")).toList());
    }

    private static String record(List<Map<String, String>> calls, Map<String, String> args, String result) {
        calls.add(args);
        return result;
    }
}
