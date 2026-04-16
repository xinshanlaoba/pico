package com.picojava.agent;

import com.picojava.tool.Tool;

public final class PromptBuilder {
    private PromptBuilder() {}

    public static String build(Pico pico, String userMessage) {
        return pico.contextManager().build(userMessage).prompt();
    }

    public static String buildPrefix(Pico pico) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 pico，一个在本地仓库中工作的编程 agent。\n");
        sb.append("你每轮必须输出一个工具调用，或者输出一个最终答案。\n");
        sb.append("工具调用可以使用 JSON 格式，例如 <tool>{\"name\":...,\"args\":{...}}</tool>。\n");
        sb.append("对于多行 write_file 或 patch_file 内容，可以使用 XML 风格，例如 <tool name=\"write_file\" path=\"a.txt\"><content>...</content></tool>。\n");
        sb.append("对于有边界的调查任务，可以使用 delegate 请求子 agent 返回摘要。\n");
        sb.append("最终答案必须使用 <final>...</final>。\n");
        sb.append("回答要具体。在对仓库状态做强判断之前，应先使用工具确认。\n\n");
        sb.append("审批策略：").append(pico.approvalPolicy()).append("\n");
        sb.append("可用工具：\n");
        for (Tool tool : pico.toolsForPrompt()) {
            sb.append("- ").append(tool.name()).append(" ").append(tool.schema())
              .append(" 高风险=").append(tool.risky())
              .append(" : ").append(tool.description()).append("\n");
        }
        return sb.toString().trim();
    }
}
