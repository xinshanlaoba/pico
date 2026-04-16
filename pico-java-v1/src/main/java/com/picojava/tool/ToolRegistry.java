package com.picojava.tool;

import com.picojava.tool.impl.ListFilesTool;
import com.picojava.tool.impl.PatchFileTool;
import com.picojava.tool.impl.ReadFileTool;
import com.picojava.tool.impl.RunShellTool;
import com.picojava.tool.impl.SearchTool;
import com.picojava.tool.impl.DelegateTool;
import com.picojava.tool.impl.WriteFileTool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        register(new ListFilesTool());
        register(new ReadFileTool());
        register(new SearchTool());
        register(new RunShellTool());
        register(new WriteFileTool());
        register(new PatchFileTool());
        register(new DelegateTool());
    }

    public void register(Tool tool) { tools.put(tool.name(), tool); }
    public Tool get(String name) { return tools.get(name); }
    public Tool find(String name) { return tools.get(name); }
    public Optional<Tool> findOptional(String name) { return Optional.ofNullable(tools.get(name)); }
    public Collection<Tool> all() { return tools.values(); }
}
