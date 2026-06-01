package com.tcode.tool;

import com.tcode.policy.PathGuard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class FileSearchService {
    private static final int MAX_RESULTS = 200;
    private static final int MAX_CONTEXT_LINES = 5;
    private static final long MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", ".tcode", "target", "node_modules", "dist", "build", "coverage", ".idea", ".gradle"
    );
    private final Supplier<PathGuard> pathGuardSupplier;

    public FileSearchService(Supplier<PathGuard> pathGuardSupplier) {
        this.pathGuardSupplier = pathGuardSupplier;
    }

    public String glob(Map<String, String> args) {
        String pattern = args.get("pattern");
        if (pattern == null || pattern.isBlank()) {
            return "文件匹配失败: pattern 不能为空";
        }
        PathGuard pathGuard = pathGuardSupplier.get();
        Path root = pathGuard.resolveSafe(args.getOrDefault("path", "."));
        int maxResults = clamp(parseInt(args.get("max_results"), 50), 1, MAX_RESULTS);
        Path projectRoot = pathGuard.getRootPath();
        PathMatcher matcher = projectRoot.getFileSystem().getPathMatcher("glob:" + normalizeGlob(pattern));
        PathMatcher fileNameMatcher = projectRoot.getFileSystem().getPathMatcher("glob:" + normalizeFileNameGlob(pattern));
        List<String> matches = new ArrayList<>();

        try {
            Files.walkFileTree(root, new SearchFileVisitor(projectRoot, path -> {
                if (matches.size() >= maxResults) {
                    return;
                }
                Path relative = projectRoot.relativize(path);
                if (matcher.matches(relative) || fileNameMatcher.matches(path.getFileName())) {
                    matches.add(displayPath(relative));
                }
            }));
        } catch (Exception e) {
            return "文件匹配失败: " + e.getMessage();
        }
        if (matches.isEmpty()) {
            return "未找到匹配文件: " + pattern;
        }
        StringBuilder result = new StringBuilder("匹配文件 ").append(matches.size()).append(" 个");
        if (matches.size() >= maxResults) {
            result.append("（已达到上限 ").append(maxResults).append("）");
        }
        result.append(":\n");
        for (int i = 0; i < matches.size(); i++) {
            result.append(i + 1).append(". ").append(matches.get(i)).append("\n");
        }
        return result.toString().trim();
    }

    public String grep(Map<String, String> args) {
        String query = args.get("pattern");
        if (query == null || query.isBlank()) {
            return "代码搜索失败: pattern 不能为空";
        }
        PathGuard pathGuard = pathGuardSupplier.get();
        Path root = pathGuard.resolveSafe(args.getOrDefault("path", "."));
        Path projectRoot = pathGuard.getRootPath();
        int maxResults = clamp(parseInt(args.get("max_results"), 50), 1, MAX_RESULTS);
        int contextLines = clamp(parseInt(args.get("context_lines"), 0), 0, MAX_CONTEXT_LINES);
        boolean regex = parseBoolean(args.get("regex"), false);
        boolean caseSensitive = parseBoolean(args.get("case_sensitive"), true);
        PathMatcher globMatcher = optionalMatcher(projectRoot, args.get("glob"), false);
        PathMatcher fileNameGlobMatcher = optionalMatcher(projectRoot, args.get("glob"), true);

        Pattern contentPattern;
        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            contentPattern = Pattern.compile(regex ? query : Pattern.quote(query), flags);
        } catch (PatternSyntaxException e) {
            return "代码搜索失败: 正则表达式无效: " + e.getMessage();
        }

        List<GrepMatch> matches = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SearchFileVisitor(projectRoot, path -> {
                if (matches.size() >= maxResults || !Files.isRegularFile(path)) {
                    return;
                }
                Path relative = projectRoot.relativize(path);
                if (globMatcher != null && !globMatcher.matches(relative)
                        && !fileNameGlobMatcher.matches(path.getFileName())) {
                    return;
                }
                collectMatches(path, relative, contentPattern, contextLines, maxResults, matches);
            }));
        } catch (Exception e) {
            return "代码搜索失败: " + e.getMessage();
        }
        if (matches.isEmpty()) {
            return "未找到匹配内容: " + query;
        }
        StringBuilder result = new StringBuilder("匹配结果 ").append(matches.size()).append(" 条");
        if (matches.size() >= maxResults) {
            result.append("（已达到上限 ").append(maxResults).append("）");
        }
        result.append(":\n");
        for (int i = 0; i < matches.size(); i++) {
            GrepMatch match = matches.get(i);
            result.append(i + 1).append(". ").append(match.file()).append(":").append(match.lineNumber()).append("\n");
            for (ContextLine line : match.context()) {
                String marker = line.lineNumber() == match.lineNumber() ? ">" : " ";
                result.append(String.format("   %s%5d | %s%n", marker, line.lineNumber(), line.text()));
            }
        }
        return result.toString().trim();
    }

    private PathMatcher optionalMatcher(Path root, String glob, boolean fileNameOnly) {
        if (glob == null || glob.isBlank()) {
            return null;
        }
        return root.getFileSystem().getPathMatcher("glob:"
                + (fileNameOnly ? normalizeFileNameGlob(glob) : normalizeGlob(glob)));
    }

    private void collectMatches(Path file, Path relative, Pattern pattern, int contextLines,
                                int maxResults, List<GrepMatch> matches) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES || isLikelyBinary(file)) {
                return;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size() && matches.size() < maxResults; i++) {
                if (pattern.matcher(lines.get(i)).find()) {
                    int from = Math.max(0, i - contextLines);
                    int to = Math.min(lines.size() - 1, i + contextLines);
                    List<ContextLine> context = new ArrayList<>();
                    for (int j = from; j <= to; j++) {
                        context.add(new ContextLine(j + 1, lines.get(j)));
                    }
                    matches.add(new GrepMatch(displayPath(relative), i + 1, context));
                }
            }
        } catch (Exception ignored) {
            // Search is fail-soft for transient files, unsupported encodings and permissions.
        }
    }

    private boolean isLikelyBinary(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        for (int i = 0; i < Math.min(bytes.length, 4096); i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) return fallback;
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim())
                || "yes".equalsIgnoreCase(value.trim());
    }

    private static String normalizeGlob(String pattern) {
        String normalized = pattern == null ? "**/*" : pattern.replace('\\', '/').trim();
        if (normalized.isEmpty()) return "**/*";
        return !normalized.contains("/") && !normalized.startsWith("**") ? "**/" + normalized : normalized;
    }

    private static String normalizeFileNameGlob(String pattern) {
        String normalized = pattern == null ? "*" : pattern.replace('\\', '/').trim();
        if (normalized.isEmpty()) return "*";
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String displayPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static final class SearchFileVisitor extends SimpleFileVisitor<Path> {
        private final Path projectRoot;
        private final java.util.function.Consumer<Path> fileConsumer;

        private SearchFileVisitor(Path projectRoot, java.util.function.Consumer<Path> fileConsumer) {
            this.projectRoot = projectRoot;
            this.fileConsumer = fileConsumer;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
            return !dir.equals(projectRoot) && EXCLUDED_DIRS.contains(name)
                    ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            fileConsumer.accept(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
        }
    }

    private record ContextLine(int lineNumber, String text) {}
    private record GrepMatch(String file, int lineNumber, List<ContextLine> context) {}
}
