package com.picojava.tool.impl;

import com.picojava.agent.Pico;
import com.picojava.tool.BaseTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class WriteFileTool extends BaseTool {
    public WriteFileTool() {
        super("write_file", true, "{path:str,content:str}", "写入文本文件。");
    }

    @Override
    public void validate(Pico pico, Map<String, Object> args) {
        Path path = pico.path((String) args.get("path"));
        if (Files.exists(path) && Files.isDirectory(path)) throw new IllegalArgumentException("path 是目录");
        if (!args.containsKey("content")) throw new IllegalArgumentException("缺少 content");
    }

    @Override
    public String execute(Pico pico, Map<String, Object> args) throws Exception {
        Path path = pico.path((String) args.get("path"));
        String content = String.valueOf(args.get("content"));
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return "已写入 " + pico.root().relativize(path) + "（" + content.length() + " 个字符）";
    }
}
