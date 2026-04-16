package com.picojava.tool.impl;

import com.picojava.agent.Pico;
import com.picojava.tool.BaseTool;
import com.picojava.workspace.WorkspaceContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListFilesTool extends BaseTool {
    public ListFilesTool() {
        super("list_files", false, "{path:str='.'}", "列出工作区中的文件。");
    }

    @Override
    public void validate(Pico pico, Map<String, Object> args) {
        Path path = pico.path(String.valueOf(args.getOrDefault("path", ".")));
        if (!Files.isDirectory(path)) throw new IllegalArgumentException("path 不是目录");
    }

    @Override
    public String execute(Pico pico, Map<String, Object> args) throws Exception {
        Path path = pico.path(String.valueOf(args.getOrDefault("path", ".")));
        List<Path> entries = Files.list(path)
                .filter(p -> !WorkspaceContext.IGNORED_PATH_NAMES.contains(p.getFileName().toString()))
                .sorted(Comparator.<Path, Boolean>comparing(Files::isRegularFile).thenComparing(p -> p.getFileName().toString().toLowerCase()))
                .limit(200)
                .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder();
        for (Path entry : entries) {
            sb.append(Files.isDirectory(entry) ? "[D] " : "[F] ")
              .append(pico.root().relativize(entry))
              .append('\n');
        }
        return sb.length() == 0 ? "(空)" : sb.toString().trim();
    }
}
