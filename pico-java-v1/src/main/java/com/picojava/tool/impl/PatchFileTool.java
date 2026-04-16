package com.picojava.tool.impl;

import com.picojava.agent.Pico;
import com.picojava.tool.BaseTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class PatchFileTool extends BaseTool {
    public PatchFileTool() {
        super("patch_file", true, "{path:str,old_text:str,new_text:str}", "替换文件中的一个精确文本块。");
    }

    @Override
    public void validate(Pico pico, Map<String, Object> args) throws Exception {
        Path path = pico.path((String) args.get("path"));
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("path 不是文件");
        String oldText = String.valueOf(args.getOrDefault("old_text", ""));
        if (oldText.isEmpty()) throw new IllegalArgumentException("old_text 不能为空");
        if (!args.containsKey("new_text")) throw new IllegalArgumentException("缺少 new_text");
        String text = Files.readString(path);
        int count = text.split(java.util.regex.Pattern.quote(oldText), -1).length - 1;
        if (count != 1) throw new IllegalArgumentException("old_text 必须恰好出现一次，实际出现 " + count + " 次");
    }

    @Override
    public String execute(Pico pico, Map<String, Object> args) throws Exception {
        Path path = pico.path((String) args.get("path"));
        String oldText = String.valueOf(args.get("old_text"));
        String newText = String.valueOf(args.get("new_text"));
        String text = Files.readString(path);
        Files.writeString(path, text.replaceFirst(java.util.regex.Pattern.quote(oldText), java.util.regex.Matcher.quoteReplacement(newText)));
        return "已修改 " + pico.root().relativize(path);
    }
}
