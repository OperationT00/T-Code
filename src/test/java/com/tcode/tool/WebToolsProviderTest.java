package com.tcode.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebToolsProviderTest {

    @Test
    void delegatesSearchAndFetchWithParsedLimits() {
        List<String> calls = new ArrayList<>();
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new WebToolsProvider(
                (query, topK) -> {
                    calls.add("search:" + query + ":" + topK);
                    return "search-ok";
                },
                (url, maxChars) -> {
                    calls.add("fetch:" + url + ":" + maxChars);
                    return "fetch-ok";
                }
        ));

        assertEquals("search-ok", registry.executeTool("web_search", "{\"query\":\"T-Code\",\"top_k\":\"7\"}"));
        assertEquals("fetch-ok", registry.executeTool("web_fetch", "{\"url\":\"https://example.com\",\"max_chars\":\"12\"}"));
        assertEquals(List.of(
                "search:T-Code:7",
                "fetch:https://example.com:12"
        ), calls);
    }
}
