package com.tcode.tool;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagToolsProviderTest {

    @Test
    void delegatesSemanticSearchWithParsedTopK() {
        AtomicReference<String> call = new AtomicReference<>();
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new RagToolsProvider((query, topK) -> {
            call.set(query + ":" + topK);
            return "rag-ok";
        }));

        assertEquals("rag-ok", registry.executeTool("search_code", "{\"query\":\"memory manager\",\"top_k\":\"9\"}"));
        assertEquals("memory manager:9", call.get());
    }
}
