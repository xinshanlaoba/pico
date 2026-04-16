package com.picojava.workspace;

import com.picojava.common.TextUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorkspaceContext {
    private static final List<String> BASELINE_FILE_PATHS = List.of(
            "AGENTS.md",
            "README.md",
            "pom.xml",
            "mvnw",
            "mvnw.cmd",
            ".mvn/wrapper/maven-wrapper.properties"
    );
    public static final Set<String> IGNORED_PATH_NAMES = Set.of(".git", ".pico", ".gradle", ".idea", ".settings", "build", "out", "target");

    private final Path cwd;
    private final Path repoRoot;
    private final String branch;
    private final String defaultBranch;
    private final String status;
    private final List<String> recentCommits;
    private final Map<String, String> projectContextFiles;

    public WorkspaceContext(Path cwd, Path repoRoot, String branch, String defaultBranch, String status,
                            List<String> recentCommits, Map<String, String> projectContextFiles) {
        this.cwd = cwd;
        this.repoRoot = repoRoot;
        this.branch = branch;
        this.defaultBranch = defaultBranch;
        this.status = status;
        this.recentCommits = recentCommits;
        this.projectContextFiles = projectContextFiles;
    }

    public static WorkspaceContext build(String cwdValue) throws IOException, InterruptedException {
        Path cwd = Path.of(cwdValue).toAbsolutePath().normalize();
        String repoRootText = git(cwd, List.of("rev-parse", "--show-toplevel"), cwd.toString());
        Path repoRoot = Path.of(repoRootText).toAbsolutePath().normalize();

        Map<String, String> contextFiles = readContextFiles(repoRoot, cwd);

        String branch = defaultString(git(cwd, List.of("branch", "--show-current"), "-"), "-");
        String remoteHead = defaultString(git(cwd, List.of("symbolic-ref", "--short", "refs/remotes/origin/HEAD"), "origin/main"), "origin/main");
        String defaultBranch = remoteHead.startsWith("origin/") ? remoteHead.substring("origin/".length()) : remoteHead;
        String status = TextUtils.clip(defaultString(git(cwd, List.of("status", "--short"), "clean"), "clean"), 1500);
        List<String> commits = new ArrayList<>();
        String commitText = git(cwd, List.of("log", "--oneline", "-5"), "");
        for (String line : commitText.split("\\R")) {
            if (!line.isBlank()) commits.add(line);
        }

        return new WorkspaceContext(cwd, repoRoot, branch, defaultBranch, status, commits, contextFiles);
    }

    public String text() {
        StringBuilder sb = new StringBuilder();
        sb.append("工作区：\n");
        sb.append("- cwd: ").append(cwd).append("\n");
        sb.append("- repo_root: ").append(repoRoot).append("\n");
        sb.append("- 当前分支：").append(branch).append("\n");
        sb.append("- 默认分支：").append(defaultBranch).append("\n");
        sb.append("- 状态：\n").append(status).append("\n");
        sb.append("- 最近提交：\n");
        if (recentCommits.isEmpty()) {
            sb.append("- 无\n");
        } else {
            for (String c : recentCommits) sb.append("- ").append(c).append("\n");
        }
        sb.append("- 项目上下文文件：\n");
        if (projectContextFiles.isEmpty()) {
            sb.append("- 无\n");
        } else {
            for (Map.Entry<String, String> entry : projectContextFiles.entrySet()) {
                sb.append("- ").append(entry.getKey()).append("\n");
                sb.append(entry.getValue()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static Map<String, String> readContextFiles(Path repoRoot, Path cwd) throws IOException {
        Map<String, String> contextFiles = new LinkedHashMap<>();
        for (Path base : List.of(repoRoot, cwd)) {
            for (String relativePath : BASELINE_FILE_PATHS) {
                Path path = base.resolve(relativePath);
                if (!Files.exists(path) || !Files.isRegularFile(path)) continue;
                String key = repoRoot.relativize(path).toString().replace('\\', '/');
                if (contextFiles.containsKey(key)) continue;
                contextFiles.put(key, TextUtils.clip(Files.readString(path, StandardCharsets.UTF_8), 1200));
            }
        }
        return contextFiles;
    }

    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(text().getBytes(StandardCharsets.UTF_8));
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String git(Path cwd, List<String> args, String fallback) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exit = process.waitFor();
        if (exit != 0 || out.isBlank()) return fallback;
        return out;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public Path cwd() { return cwd; }
    public Path repoRoot() { return repoRoot; }
    public String branch() { return branch; }
    public String defaultBranch() { return defaultBranch; }
    public String status() { return status; }
}
