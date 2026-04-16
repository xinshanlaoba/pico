package com.picojava.tool.impl;

import com.picojava.agent.Pico;
import com.picojava.tool.BaseTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ReadFileTool extends BaseTool {
    public ReadFileTool() {
        super("read_file", false, "{path:str,start:int=1,end:int=200}", "按行范围读取 UTF-8 文件。");
    }

    @Override
    public void validate(Pico pico, Map<String, Object> args) {
        Path path = pico.path((String) args.get("path"));
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("path 不是文件");
        int start = ((Number) args.getOrDefault("start", 1)).intValue();
        int end = ((Number) args.getOrDefault("end", 200)).intValue();
        if (start < 1 || end < start) throw new IllegalArgumentException("行号范围无效");
    }

    @Override
    public String execute(Pico pico, Map<String, Object> args) throws Exception {
        Path path = pico.path((String) args.get("path"));
        int start = ((Number) args.getOrDefault("start", 1)).intValue();
        int end = ((Number) args.getOrDefault("end", 200)).intValue();
        List<String> lines = Files.readAllLines(path);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(pico.root().relativize(path)).append('\n');
        for (int i = start; i <= Math.min(end, lines.size()); i++) {
            sb.append(String.format("%4d: %s%n", i, lines.get(i - 1)));
        }
        return sb.toString().trim();
    }
}
