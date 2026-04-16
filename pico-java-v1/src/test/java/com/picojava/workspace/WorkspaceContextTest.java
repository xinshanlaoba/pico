package com.picojava.workspace;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class WorkspaceContextTest {
    @Test
    void ignoredContainsWorkspaceMetadataAndJavaBuildOutputs() {
        Assertions.assertTrue(WorkspaceContext.IGNORED_PATH_NAMES.contains(".pico"));
        Assertions.assertTrue(WorkspaceContext.IGNORED_PATH_NAMES.contains(".gradle"));
        Assertions.assertTrue(WorkspaceContext.IGNORED_PATH_NAMES.contains("build"));
        Assertions.assertTrue(WorkspaceContext.IGNORED_PATH_NAMES.contains("out"));
        Assertions.assertTrue(WorkspaceContext.IGNORED_PATH_NAMES.contains("target"));
        Assertions.assertFalse(WorkspaceContext.IGNORED_PATH_NAMES.contains(".venv"));
    }

    @Test
    void textIncludesWorkspaceFactsAndFingerprintIsStable() {
        WorkspaceContext context = new WorkspaceContext(
                Path.of("C:/repo"),
                Path.of("C:/repo"),
                "feature/test",
                "main",
                "M README.md",
                List.of("abc123 demo commit"),
                Map.of("README.md", "Project docs")
        );

        String firstText = context.text();
        String secondText = context.text();

        Assertions.assertTrue(firstText.contains("feature/test"));
        Assertions.assertTrue(firstText.contains("abc123 demo commit"));
        Assertions.assertTrue(firstText.contains("README.md"));
        Assertions.assertTrue(firstText.contains("项目上下文文件"));
        Assertions.assertEquals(firstText, secondText);
        Assertions.assertEquals(context.fingerprint(), context.fingerprint());
    }

    @Test
    void buildCollectsMavenBaselineFilesAndSkipsNonMavenFiles(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# Demo\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("mvnw"), "echo mvnw\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle.kts"), "plugins {}", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("pyproject.toml"), "[project]\nname = \"demo\"\n", StandardCharsets.UTF_8);

        git(tempDir, "init");
        git(tempDir, "config", "user.name", "WorkspaceContextTest");
        git(tempDir, "config", "user.email", "workspace@example.com");
        git(tempDir, "add", ".");
        git(tempDir, "commit", "-m", "Initial import");

        WorkspaceContext context = WorkspaceContext.build(tempDir.toString());
        String text = context.text();

        Assertions.assertTrue(text.contains("项目上下文文件"));
        Assertions.assertTrue(text.contains("README.md"));
        Assertions.assertTrue(text.contains("pom.xml"));
        Assertions.assertTrue(text.contains("mvnw"));
        Assertions.assertFalse(text.contains("build.gradle.kts"));
        Assertions.assertFalse(text.contains("pyproject.toml"));
        Assertions.assertTrue(text.indexOf("README.md") < text.indexOf("pom.xml"));
    }

    private static void git(Path directory, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        Assertions.assertEquals(0, exit, output);
    }
}
