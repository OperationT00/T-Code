package com.tcode.tool;

import com.tcode.rag.CodeRetriever;
import com.tcode.rag.SearchResultFormatter;
import com.tcode.rag.VectorStore;

import java.util.List;
import java.util.function.Supplier;

public final class RagSearchService {
    private final Supplier<String> projectPathSupplier;

    public RagSearchService(Supplier<String> projectPathSupplier) {
        this.projectPathSupplier = projectPathSupplier;
    }

    public String search(String query, int topK) {
        int normalizedTopK = Math.max(1, Math.min(topK, 30));
        try (CodeRetriever retriever = new CodeRetriever(projectPathSupplier.get())) {
            var stats = retriever.getStats();
            if (stats.chunkCount() == 0) {
                return "代码库尚未索引，请先使用 /index 命令索引当前项目。";
            }
            List<VectorStore.SearchResult> results = retriever.hybridSearch(query, normalizedTopK);
            if (results.isEmpty()) {
                return "未找到与查询相关的代码。";
            }
            return SearchResultFormatter.formatForTool(query, results);
        } catch (Exception e) {
            return "代码检索失败: " + e.getMessage();
        }
    }
}
