package com.tcode.tool;

import com.tcode.policy.PathGuard;
import com.tcode.policy.PolicyException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class FileService {
    private static final int MAX_WRITE_FILE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_READ_FILE_LINES = 2000;

    private final Supplier<PathGuard> pathGuardSupplier;
    private final Supplier<BiConsumer<String, String[]>> writeObserverSupplier;
    private final BiConsumer<String, Path> postEditHook;

    public FileService(Supplier<PathGuard> pathGuardSupplier,
                       Supplier<BiConsumer<String, String[]>> writeObserverSupplier,
                       BiConsumer<String, Path> postEditHook) {
        this.pathGuardSupplier = pathGuardSupplier;
        this.writeObserverSupplier = writeObserverSupplier;
        this.postEditHook = postEditHook == null ? (path, safe) -> {} : postEditHook;
    }

    public String read(Map<String, String> args) {
        Path safe = pathGuard().resolveSafe(args.get("path"));
        try {
            return readFileForTool(safe, args);
        } catch (Exception e) {
            return "读取文件失败: " + e.getMessage();
        }
    }

    public String write(Map<String, String> args) {
        String path = args.get("path");
        String content = args.get("content") == null ? "" : args.get("content");
        int contentBytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (contentBytes > MAX_WRITE_FILE_BYTES) {
            throw new PolicyException("写入内容 " + contentBytes + " 字节超过 "
                    + (MAX_WRITE_FILE_BYTES / 1024 / 1024) + "MB 上限");
        }
        Path safe = pathGuard().resolveSafe(path);
        String before = readExistingText(safe);
        try {
            Path parent = safe.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(safe, content);
            notifyWriteObserver(path, before, content);
            postEditHook.accept(path, safe);
            return "文件已写入: " + path;
        } catch (Exception e) {
            return "写入文件失败: " + e.getMessage();
        }
    }

    public String list(Map<String, String> args) {
        Path safe = pathGuard().resolveSafe(args.get("path"));
        try {
            File[] files = safe.toFile().listFiles();
            if (files == null) {
                return "目录为空或不存在";
            }
            StringBuilder sb = new StringBuilder("目录内容:\n");
            for (File file : files) {
                sb.append(file.isDirectory() ? "[D] " : "[F] ")
                        .append(file.getName())
                        .append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "列出目录失败: " + e.getMessage();
        }
    }

    private String readFileForTool(Path file, Map<String, String> args) throws IOException {
        if (!Files.isRegularFile(file)) {
            return "读取文件失败: 不是普通文件";
        }
        boolean ranged = args.containsKey("offset") || args.containsKey("limit");
        if (!ranged) {
            return "文件内容:\n" + Files.readString(file);
        }
        int offset = Math.max(1, parseInt(args.get("offset"), 1));
        int limit = Math.max(1, Math.min(parseInt(args.get("limit"), 200), MAX_READ_FILE_LINES));
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int total = lines.size();
        if (offset > total) {
            return "文件内容: " + file.getFileName() + " 共 " + total + " 行，offset 超出范围";
        }
        int from = offset - 1;
        int to = Math.min(from + limit, total);
        StringBuilder sb = new StringBuilder();
        sb.append("文件内容: ").append(file.getFileName())
                .append(" (lines ").append(offset).append("-").append(to)
                .append(" of ").append(total).append(")\n");
        for (int i = from; i < to; i++) {
            sb.append(String.format("%5d | %s%n", i + 1, lines.get(i)));
        }
        if (to < total) {
            sb.append("...(已截断，可用 offset=").append(to + 1).append(" 继续读取)");
        }
        return sb.toString().trim();
    }

    private PathGuard pathGuard() {
        return pathGuardSupplier.get();
    }

    private String readExistingText(Path safe) {
        try {
            return Files.exists(safe) && Files.isRegularFile(safe) ? Files.readString(safe) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void notifyWriteObserver(String path, String before, String content) {
        try {
            BiConsumer<String, String[]> observer = writeObserverSupplier.get();
            if (observer != null) {
                observer.accept(path, new String[]{before, content});
            }
        } catch (Exception ignored) {
            // observer 失败不能影响 write_file 主路径
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
