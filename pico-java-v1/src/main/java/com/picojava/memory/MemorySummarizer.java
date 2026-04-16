package com.picojava.memory;

import com.picojava.session.MessageEntry;
import com.picojava.workspace.WorkspaceContext;

import java.util.List;

@FunctionalInterface
public interface MemorySummarizer {
    SummarySuggestion summarize(SummaryRequest request) throws Exception;

    static MemorySummarizer noop() {
        return request -> SummarySuggestion.noop();
    }

    record SummaryRequest(
            List<MessageEntry> history,
            MemoryState memoryState,
            WorkspaceContext workspaceContext,
            String latestUserMessage
    ) {}

    record SummarySuggestion(
            boolean updated,
            String summaryMemory
    ) {
        public static SummarySuggestion noop() {
            return new SummarySuggestion(false, "");
        }
    }
}
