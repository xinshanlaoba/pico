package com.picojava.tool.impl;

import com.picojava.agent.Pico;
import com.picojava.tool.BaseTool;
import com.picojava.workspace.WorkspaceContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchTool extends BaseTool {
    public SearchTool() {
        super("search", false, "{pattern:str,path:str='.'}", "在工作区中搜索文本。");
    }

    @Override
    public void validate(Pico pico, Map<String, Object> args) {
        String pattern = String.valueOf(args.getOrDefault("pattern", "")).trim();
        if (pattern.isEmpty()) throw new IllegalArgumentException("pattern 不能为空");
        pico.path(String.valueOf(args.getOrDefault("path", ".")));
    }

    @Override
    public String execute(Pico pico, Map<String, Object> args) throws Exception {
        String pattern = String.valueOf(args.getOrDefault("pattern", "")).trim().toLowerCase();
        Path path = pico.path(String.valueOf(args.getOrDefault("path", ".")));
        List<String> matches = new ArrayList<>();
        if (Files.isRegularFile(path)) {
            searchFile(pico, path, pattern, matches);
        } else {
            try (var stream = Files.walk(path)) {
                for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                    if (fileSkippable(pico, file)) continue;
                    searchFile(pico, file, pattern, matches);
                    if (matches.size() >= 200) break;
                }
            }
        }
        return matches.isEmpty() ? "(无匹配)" : String.join("\n", matches);
    }

    private static void searchFile(Pico pico, Path file, String pattern, List<String> matches) throws Exception {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).toLowerCase().contains(pattern)) {
                matches.add(pico.root().relativize(file) + ":" + (i + 1) + ":" + lines.get(i));
                if (matches.size() >= 200) return;
            }
        }
    }

    private static boolean fileSkippable(Pico pico, Path file) {
        for (Path part : pico.root().relativize(file)) {
            if (WorkspaceContext.IGNORED_PATH_NAMES.contains(part.toString())) return true;
        }
        return false;
    }
}
