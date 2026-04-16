package com.picojava.run;

import com.picojava.common.JsonUtils;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class RunStore {
    private final Path root;

    public RunStore(Path root) {
        this.root = root;
    }

    public Path startRun(TaskState taskState) throws IOException {
        Path runDir = runDir(taskState.getRunId());
        Files.createDirectories(runDir);
        if (!taskState.getParentRunId().isBlank()) {
            Files.createDirectories(childLinksDir(taskState.getParentRunId()));
        }
        writeTaskState(taskState);
        return runDir;
    }

    public void writeTaskState(TaskState taskState) throws IOException {
        writeJsonAtomic(taskStatePath(taskState.getRunId()), taskState);
    }

    public TraceWriter traceWriter(String runId) {
        return new TraceWriter(tracePath(runId));
    }

    public ReportWriter reportWriter(String runId) {
        return new ReportWriter(reportPath(runId));
    }

    public void linkChildRun(String parentRunId, TaskState.ChildRun childRun) throws IOException {
        if (parentRunId == null || parentRunId.isBlank() || childRun == null || childRun.getRunId().isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parent_run_id", parentRunId);
        payload.put("child_run", childRun);
        payload.put("child_run_path", runDir(childRun.getRunId()).toString());
        writeJsonAtomic(childLinkPath(parentRunId, childRun.getRunId()), payload);
    }

    public Path runDir(String runId) {
        return root.resolve(runId);
    }

    public Path childLinksDir(String parentRunId) {
        return runDir(parentRunId).resolve("children");
    }

    public Path childLinkPath(String parentRunId, String childRunId) {
        return childLinksDir(parentRunId).resolve(childRunId + ".json");
    }

    public Path taskStatePath(String runId) {
        return runDir(runId).resolve("task_state.json");
    }

    public Path tracePath(String runId) {
        return runDir(runId).resolve("trace.jsonl");
    }

    public Path reportPath(String runId) {
        return runDir(runId).resolve("report.json");
    }

    static void writeJsonAtomic(Path path, Object payload) throws IOException {
        Files.createDirectories(path.getParent());
        Path tempFile = Files.createTempFile(path.getParent(), path.getFileName().toString() + ".", ".tmp");
        try {
            JsonUtils.MAPPER.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), payload);
            try {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
