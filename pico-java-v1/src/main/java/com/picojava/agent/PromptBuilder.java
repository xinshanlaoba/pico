package com.picojava.agent;

import com.picojava.tool.Tool;

public final class PromptBuilder {
    private PromptBuilder() {}

    public static String build(Pico pico, String userMessage) {
        return pico.contextManager().build(userMessage).prompt();
    }

    public static String buildPrefix(Pico pico) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are pico, a coding agent operating inside a local repository.\n");
        sb.append("On each turn, either make exactly one tool call or provide a final answer.\n");
        if (pico.modelClient().supportsNativeToolCalling()) {
            sb.append("When tool definitions are attached, prefer native tool calling over textual <tool> blocks.\n");
            sb.append("If native tool calling is unavailable, you may fall back to <tool>{\"name\":...,\"args\":{...}}</tool>.\n");
        } else {
            sb.append("Tool calls may be returned as <tool>{\"name\":...,\"args\":{...}}</tool>.\n");
        }
        sb.append("For multi-line write_file or patch_file content, you may use XML style tool calls such as ");
        sb.append("<tool name=\"write_file\" path=\"a.txt\"><content>...</content></tool>.\n");
        sb.append("Use delegate only for bounded side investigations and return a concise summary.\n");
        sb.append("Final answers should use <final>...</final> whenever possible.\n");
        sb.append("Be concrete. Verify repository state with tools before making strong claims.\n\n");
        sb.append("Approval policy: ").append(pico.approvalPolicy()).append('\n');
        sb.append("Available tools:\n");
        for (Tool tool : pico.toolsForPrompt()) {
            sb.append("- ").append(tool.name()).append(' ')
                    .append(tool.schema())
                    .append(" risky=").append(tool.risky())
                    .append(" : ").append(tool.description())
                    .append('\n');
        }
        return sb.toString().trim();
    }
}
