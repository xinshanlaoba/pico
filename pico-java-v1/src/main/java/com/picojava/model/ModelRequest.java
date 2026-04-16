package com.picojava.model;

import com.picojava.tool.Tool;

import java.util.Collection;
import java.util.List;

public record ModelRequest(
        String prompt,
        int maxNewTokens,
        List<ModelToolDefinition> toolDefinitions
) {
    public ModelRequest {
        prompt = prompt == null ? "" : prompt;
        if (maxNewTokens <= 0) {
            maxNewTokens = 1;
        }
        toolDefinitions = toolDefinitions == null ? List.of() : List.copyOf(toolDefinitions);
    }

    public ModelRequest(String prompt, int maxNewTokens) {
        this(prompt, maxNewTokens, List.of());
    }

    public static ModelRequest withTools(String prompt, int maxNewTokens, Collection<Tool> tools) {
        return new ModelRequest(prompt, maxNewTokens, ModelToolDefinition.fromTools(tools));
    }
}
