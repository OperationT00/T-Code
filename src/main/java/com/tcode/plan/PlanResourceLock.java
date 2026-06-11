package com.tcode.plan;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PlanResourceLock(String value) {
    private static final Pattern BACKTICK_TOKEN = Pattern.compile("`([^`]+)`");
    private static final Pattern PATH_TOKEN = Pattern.compile("(?<![\\w@.-])([\\w./\\\\-]+\\.[A-Za-z0-9]{1,12})(?![\\w@.-])");

    public PlanResourceLock {
        value = normalize(value);
    }

    public static Set<String> infer(Task task) {
        Set<String> locks = new LinkedHashSet<>();
        if (task == null) {
            return locks;
        }
        for (String lock : task.getResourceLocks()) {
            String normalized = normalize(lock);
            if (!normalized.isBlank()) {
                locks.add(normalized);
            }
        }
        if (task.getType() == Task.TaskType.FILE_WRITE) {
            inferFileLocksFromDescription(task.getDescription()).forEach(locks::add);
        }
        if (task.getType() == Task.TaskType.COMMAND) {
            locks.add("tool:shell");
        }
        if (task.getType() == Task.TaskType.FILE_WRITE && locks.stream().noneMatch(lock -> lock.startsWith("file:"))) {
            locks.add("tool:file-write");
        }
        return locks;
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().replace('\\', '/');
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        if (value.matches("^[A-Za-z]:/.*")) {
            return "file:" + value;
        }
        int colon = value.indexOf(':');
        if (colon < 0) {
            return value.isBlank() ? "" : "file:" + value;
        }

        String prefix = value.substring(0, colon).trim().toLowerCase();
        String body = value.substring(colon + 1).trim();
        while (body.startsWith("./")) {
            body = body.substring(2);
        }
        if (body.isBlank()) {
            return "";
        }
        if ("tool".equals(prefix)) {
            body = body.toLowerCase();
        }
        return prefix + ":" + body;
    }

    public static boolean conflicts(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) {
            return false;
        }
        if (normalizedLeft.equals(normalizedRight)) {
            return true;
        }

        LockParts leftParts = LockParts.parse(normalizedLeft);
        LockParts rightParts = LockParts.parse(normalizedRight);
        if (leftParts == null || rightParts == null) {
            return false;
        }
        if ("dir".equals(leftParts.prefix()) && ("file".equals(rightParts.prefix()) || "dir".equals(rightParts.prefix()))) {
            return isUnderDirectory(rightParts.body(), leftParts.body());
        }
        if ("dir".equals(rightParts.prefix()) && ("file".equals(leftParts.prefix()) || "dir".equals(leftParts.prefix()))) {
            return isUnderDirectory(leftParts.body(), rightParts.body());
        }
        return false;
    }

    private static Set<String> inferFileLocksFromDescription(String description) {
        Set<String> locks = new LinkedHashSet<>();
        if (description == null || description.isBlank()) {
            return locks;
        }

        Matcher backtickMatcher = BACKTICK_TOKEN.matcher(description);
        while (backtickMatcher.find()) {
            addFileLockIfPathLike(locks, backtickMatcher.group(1));
        }

        Matcher pathMatcher = PATH_TOKEN.matcher(description);
        while (pathMatcher.find()) {
            addFileLockIfPathLike(locks, pathMatcher.group(1));
        }
        return locks;
    }

    private static void addFileLockIfPathLike(Set<String> locks, String candidate) {
        String normalized = normalize(cleanPathCandidate(candidate));
        if (!normalized.startsWith("file:")) {
            return;
        }
        String path = normalized.substring("file:".length());
        if (path.contains("/") || path.matches("(?i).+\\.(java|kt|ts|tsx|js|jsx|py|go|rs|xml|json|yaml|yml|md|txt|properties|gradle|pom|sql|sh|ps1|bat|html|css)$")) {
            locks.add(normalized);
        }
    }

    private static String cleanPathCandidate(String candidate) {
        if (candidate == null) {
            return "";
        }
        String value = candidate.trim();
        while (!value.isEmpty() && "([{\"'".indexOf(value.charAt(0)) >= 0) {
            value = value.substring(1).trim();
        }
        while (!value.isEmpty() && ")]}\"',.;:".indexOf(value.charAt(value.length() - 1)) >= 0) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }

    private static boolean isUnderDirectory(String path, String directory) {
        String normalizedPath = normalizePathBody(path);
        String normalizedDirectory = normalizePathBody(directory);
        return normalizedPath.equals(normalizedDirectory) || normalizedPath.startsWith(normalizedDirectory + "/");
    }

    private static String normalizePathBody(String body) {
        String value = body == null ? "" : body.trim().replace('\\', '/');
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private record LockParts(String prefix, String body) {
        static LockParts parse(String lock) {
            int colon = lock.indexOf(':');
            if (colon < 0 || colon == lock.length() - 1) {
                return null;
            }
            return new LockParts(lock.substring(0, colon), lock.substring(colon + 1));
        }
    }
}
