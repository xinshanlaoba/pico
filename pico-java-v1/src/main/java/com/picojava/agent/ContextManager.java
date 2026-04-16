package com.picojava.agent;

import com.picojava.common.TextUtils;
import com.picojava.memory.MemorySummarizer;
import com.picojava.session.MessageEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContextManager {
    private static final int TOTAL_PROMPT_LIMIT = 10_000;
    private static final int USER_MESSAGE_LIMIT = 2_500;
    private static final int HISTORY_TARGET_CHARS = 3_600;
    private static final int HISTORY_COMPRESSED_CHARS = 1_700;
    private static final int HISTORY_TIGHT_CHARS = 900;
    private static final int HISTORY_TARGET_MESSAGES = 10;
    private static final int HISTORY_COMPRESSED_MESSAGES = 6;
    private static final int HISTORY_TIGHT_MESSAGES = 4;
    private static final int HISTORY_MESSAGE_CLIP = 500;
    private static final int HISTORY_COMPRESSED_MESSAGE_CLIP = 260;
    private static final int HISTORY_TIGHT_MESSAGE_CLIP = 180;
    private static final int WORKSPACE_TARGET_LIMIT = 1_800;
    private static final int WORKSPACE_COMPRESSED_LIMIT = 900;
    private static final int WORKSPACE_TIGHT_LIMIT = 500;
    private static final int TOOL_RESULTS_TARGET_LIMIT = 1_400;
    private static final int TOOL_RESULTS_COMPRESSED_LIMIT = 700;
    private static final int TOOL_RESULTS_TIGHT_LIMIT = 360;

    private final Pico pico;
    private final MemorySummarizer summarizer;

    public ContextManager(Pico pico) {
        this(pico, MemorySummarizer.noop());
    }

    public ContextManager(Pico pico, MemorySummarizer summarizer) {
        this.pico = pico;
        this.summarizer = summarizer == null ? MemorySummarizer.noop() : summarizer;
    }

    public BuildResult build(String userMessage) {
        String prefix = PromptBuilder.buildPrefix(pico);
        String safeUserMessage = TextUtils.clip(userMessage == null ? "" : userMessage.trim(), USER_MESSAGE_LIMIT);
        String requestSection = "当前用户请求：\n" + safeUserMessage;
        String summarySection = pico.memory().summarySectionText();
        String workspaceSection = pico.memory().workspaceSummarySectionText(pico.workspace().text(), WORKSPACE_TARGET_LIMIT);
        String toolResultsSection = clipSection(
                pico.memory().recentToolResultsSectionText(),
                TOOL_RESULTS_TARGET_LIMIT
        );
        HistorySection historySection = renderHistory(
                safeUserMessage,
                HISTORY_TARGET_MESSAGES,
                HISTORY_TARGET_CHARS,
                HISTORY_MESSAGE_CLIP
        );

        PromptSections sections = new PromptSections(
                prefix,
                workspaceSection,
                summarySection,
                toolResultsSection,
                historySection.text(),
                requestSection
        );
        boolean compressed = false;
        if (sections.prompt().length() > TOTAL_PROMPT_LIMIT) {
            compressed = true;
            sections = new PromptSections(
                    prefix,
                    pico.memory().workspaceSummarySectionText(pico.workspace().text(), WORKSPACE_COMPRESSED_LIMIT),
                    summarySection,
                    clipSection(pico.memory().recentToolResultsSectionText(), TOOL_RESULTS_COMPRESSED_LIMIT),
                    renderHistory(safeUserMessage, HISTORY_COMPRESSED_MESSAGES, HISTORY_COMPRESSED_CHARS, HISTORY_COMPRESSED_MESSAGE_CLIP).text(),
                    requestSection
            );
            historySection = renderHistory(
                    safeUserMessage,
                    HISTORY_COMPRESSED_MESSAGES,
                    HISTORY_COMPRESSED_CHARS,
                    HISTORY_COMPRESSED_MESSAGE_CLIP
            );
        }
        if (sections.prompt().length() > TOTAL_PROMPT_LIMIT) {
            compressed = true;
            sections = new PromptSections(
                    prefix,
                    pico.memory().workspaceSummarySectionText(pico.workspace().text(), WORKSPACE_TIGHT_LIMIT),
                    summarySection,
                    clipSection(pico.memory().recentToolResultsSectionText(), TOOL_RESULTS_TIGHT_LIMIT),
                    renderHistory(safeUserMessage, HISTORY_TIGHT_MESSAGES, HISTORY_TIGHT_CHARS, HISTORY_TIGHT_MESSAGE_CLIP).text(),
                    requestSection
            );
            historySection = renderHistory(
                    safeUserMessage,
                    HISTORY_TIGHT_MESSAGES,
                    HISTORY_TIGHT_CHARS,
                    HISTORY_TIGHT_MESSAGE_CLIP
            );
        }

        MemorySummarizer.SummarySuggestion summarySuggestion = MemorySummarizer.SummarySuggestion.noop();
        if (compressed && historySection.omittedMessages() > 0) {
            try {
                summarySuggestion = summarizer.summarize(new MemorySummarizer.SummaryRequest(
                        List.copyOf(pico.session().getHistory()),
                        pico.memory().state(),
                        pico.workspace(),
                        safeUserMessage
                ));
            } catch (Exception ignored) {
                summarySuggestion = MemorySummarizer.SummarySuggestion.noop();
            }
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("compressed", compressed);
        metadata.put("summary_refresh_suggested", compressed && historySection.omittedMessages() > 0);
        metadata.put("summary_updated", summarySuggestion.updated());
        metadata.put("prompt_chars", sections.prompt().length());
        metadata.put("history_messages_included", historySection.includedMessages());
        metadata.put("history_messages_omitted", historySection.omittedMessages());
        metadata.put("prefix_chars", sections.prefix().length());
        metadata.put("workspace_chars", sections.workspace().length());
        metadata.put("summary_chars", sections.summary().length());
        metadata.put("tool_results_chars", sections.toolResults().length());
        metadata.put("history_chars", sections.history().length());
        metadata.put("request_chars", sections.request().length());
        metadata.put("prompt_preview", TextUtils.clip(sections.prompt(), 1200));
        return new BuildResult(sections.prompt(), metadata);
    }

    private HistorySection renderHistory(String currentUserMessage, int maxMessages, int maxChars, int messageClip) {
        List<MessageEntry> history = pico.session().getHistory();
        List<String> selected = new ArrayList<>();
        int consumedChars = 0;
        int includedMessages = 0;
        int omittedMessages = 0;
        boolean skippedCurrentUser = false;

        for (int i = history.size() - 1; i >= 0; i--) {
            MessageEntry entry = history.get(i);
            String role = entry.getRole() == null ? "unknown" : entry.getRole();
            String content = entry.getContent() == null ? "" : entry.getContent();
            if (!skippedCurrentUser && "user".equals(role) && content.equals(currentUserMessage)) {
                skippedCurrentUser = true;
                omittedMessages++;
                continue;
            }
            String line = "[" + role + "] " + TextUtils.clip(content, messageClip);
            int lineLength = line.length() + 1;
            if (includedMessages >= maxMessages || consumedChars + lineLength > maxChars) {
                omittedMessages = i + 1;
                break;
            }
            selected.add(0, line);
            consumedChars += lineLength;
            includedMessages++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("会话历史：\n");
        if (selected.isEmpty()) {
            sb.append("- 空");
            return new HistorySection(sb.toString(), 0, history.isEmpty() ? 0 : history.size());
        }
        if (omittedMessages > 0) {
            sb.append("- ... 已省略 ").append(omittedMessages).append(" 条更早消息\n");
        }
        for (int i = 0; i < selected.size(); i++) {
            sb.append(selected.get(i));
            if (i < selected.size() - 1) {
                sb.append('\n');
            }
        }
        return new HistorySection(sb.toString(), includedMessages, omittedMessages);
    }

    private String clipSection(String section, int limit) {
        if (section == null || section.isBlank()) {
            return "";
        }
        return TextUtils.clip(section, limit);
    }

    private record PromptSections(
            String prefix,
            String workspace,
            String summary,
            String toolResults,
            String history,
            String request
    ) {
        private String prompt() {
            List<String> sections = new ArrayList<>();
            addIfPresent(sections, prefix);
            addIfPresent(sections, workspace);
            addIfPresent(sections, summary);
            addIfPresent(sections, toolResults);
            addIfPresent(sections, history);
            addIfPresent(sections, request);
            return String.join("\n\n", sections).trim();
        }

        private static void addIfPresent(List<String> sections, String value) {
            if (value != null && !value.isBlank()) {
                sections.add(value.trim());
            }
        }
    }

    private record HistorySection(
            String text,
            int includedMessages,
            int omittedMessages
    ) {}

    public record BuildResult(
            String prompt,
            Map<String, Object> metadata
    ) {}
}
