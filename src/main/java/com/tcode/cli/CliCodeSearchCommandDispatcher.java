package com.tcode.cli;

import com.tcode.memory.MemoryManager;
import com.tcode.rag.CodeIndex;
import com.tcode.rag.CodeRelation;
import com.tcode.rag.CodeRetriever;
import com.tcode.rag.SearchResultFormatter;
import com.tcode.tool.ToolRegistry;

import java.io.File;
import java.io.PrintStream;
import java.util.List;

final class CliCodeSearchCommandDispatcher {

    record Context(PrintStream ui, ToolRegistry toolRegistry, MemoryManager memoryManager) {
    }

    private CliCodeSearchCommandDispatcher() {
    }

    static boolean dispatch(CliCommandParser.ParsedCommand command, Context context) {
        return switch (command.type()) {
            case INDEX_CODE -> {
                index(command.payload(), context);
                yield true;
            }
            case SEARCH_CODE -> {
                search(command.payload(), context.ui());
                yield true;
            }
            case GRAPH_QUERY -> {
                graph(command.payload(), context.ui());
                yield true;
            }
            default -> false;
        };
    }

    private static void index(String payload, Context context) {
        String indexPath = payload != null ? payload : ".";
        CodeIndex indexer = new CodeIndex(context.ui()::println);
        indexer.index(indexPath);
        context.ui().println();

        String absPath = new File(indexPath).getAbsolutePath();
        context.toolRegistry().setProjectPath(absPath);
        context.memoryManager().setProjectPath(absPath);
    }

    private static void search(String query, PrintStream out) {
        if (query == null || query.isEmpty()) {
            out.println("❌ 请提供检索关键词，例如 /search 用户登录实现\n");
            return;
        }
        out.println("🔍 检索: " + query);
        try (CodeRetriever retriever = new CodeRetriever(".")) {
            var stats = retriever.getStats();
            if (stats.chunkCount() == 0) {
                out.println("⚠️ 代码库尚未索引，请先使用 /index 命令\n");
                return;
            }
            List<com.tcode.rag.VectorStore.SearchResult> results = retriever.hybridSearch(query, 5);
            if (results.isEmpty()) {
                out.println("📭 未找到相关代码\n");
            } else {
                out.println(SearchResultFormatter.formatForCli(query, results) + "\n");
            }
        } catch (Exception e) {
            out.println("❌ 检索失败: " + e.getMessage() + "\n");
        }
    }

    private static void graph(String className, PrintStream out) {
        if (className == null || className.isEmpty()) {
            out.println("❌ 请提供类名，例如 /graph Main\n");
            return;
        }
        out.println("🕸️ 查询类关系图谱: " + className);
        try (CodeRetriever retriever = new CodeRetriever(".")) {
            var stats = retriever.getStats();
            if (stats.chunkCount() == 0) {
                out.println("⚠️ 代码库尚未索引，请先使用 /index 命令\n");
                return;
            }
            List<CodeRelation> relations = retriever.getRelationGraph(className);
            if (relations.isEmpty()) {
                out.println("📭 未找到相关关系\n");
                return;
            }
            out.println("📋 找到 " + relations.size() + " 条关系:\n");
            for (CodeRelation rel : relations) {
                String arrow = rel.relationType().equals("contains") ? "├── contains -->"
                        : rel.relationType().equals("extends") ? "└── extends -->"
                        : rel.relationType().equals("implements") ? "└── implements -->"
                        : rel.relationType().equals("calls") ? "├── calls -->"
                        : "├── " + rel.relationType() + " -->";
                out.printf("   %s %s [%s]%n", rel.fromName(), arrow,
                        rel.toName() != null ? rel.toName() : "unknown");
            }
            out.println();
        } catch (Exception e) {
            out.println("❌ 查询失败: " + e.getMessage() + "\n");
        }
    }
}
