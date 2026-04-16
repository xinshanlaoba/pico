package com.picojava.agent;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolCall(String name, Map<String, Object> args) {
    public ToolCall {
        args = args == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(args));
    }
}
