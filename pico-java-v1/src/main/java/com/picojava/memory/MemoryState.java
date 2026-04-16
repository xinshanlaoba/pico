package com.picojava.memory;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MemoryState {
    private String taskSummary = "";
    private String summaryMemory = "";
    private List<String> recentFiles = new ArrayList<>();
    private List<RecentToolResult> recentToolResults = new ArrayList<>();
    private Map<String, Object> extensions = new LinkedHashMap<>();

    public void normalize() {
        if (taskSummary == null) taskSummary = "";
        if (summaryMemory == null) summaryMemory = "";
        if (recentFiles == null) recentFiles = new ArrayList<>();
        if (recentToolResults == null) recentToolResults = new ArrayList<>();
        if (extensions == null) extensions = new LinkedHashMap<>();
        recentToolResults.forEach(RecentToolResult::normalize);
    }

    public String getTaskSummary() {
        return taskSummary;
    }

    public void setTaskSummary(String taskSummary) {
        this.taskSummary = taskSummary;
    }

    public String getSummaryMemory() {
        return summaryMemory;
    }

    public void setSummaryMemory(String summaryMemory) {
        this.summaryMemory = summaryMemory;
    }

    public List<String> getRecentFiles() {
        return recentFiles;
    }

    public void setRecentFiles(List<String> recentFiles) {
        this.recentFiles = recentFiles;
    }

    public List<RecentToolResult> getRecentToolResults() {
        return recentToolResults;
    }

    public void setRecentToolResults(List<RecentToolResult> recentToolResults) {
        this.recentToolResults = recentToolResults;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }

    public static class RecentToolResult {
        private String toolName = "";
        private String status = "";
        private String argsSummary = "";
        private String resultSummary = "";
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant createdAt;

        public void normalize() {
            if (toolName == null) toolName = "";
            if (status == null) status = "";
            if (argsSummary == null) argsSummary = "";
            if (resultSummary == null) resultSummary = "";
            if (createdAt == null) createdAt = Instant.now();
        }

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getArgsSummary() {
            return argsSummary;
        }

        public void setArgsSummary(String argsSummary) {
            this.argsSummary = argsSummary;
        }

        public String getResultSummary() {
            return resultSummary;
        }

        public void setResultSummary(String resultSummary) {
            this.resultSummary = resultSummary;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
    }
}
