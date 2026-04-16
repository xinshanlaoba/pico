package com.picojava.tool.impl;

import com.picojava.agent.Pico;
import com.picojava.tool.BaseTool;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RunShellTool extends BaseTool {
    public RunShellTool() {
        super("run_shell", true, "{command:str,timeout:int=20}", "在仓库根目录执行 shell 命令。");
    }

    @Override
    public void validate(Pico pico, Map<String, Object> args) {
        String command = String.valueOf(args.getOrDefault("command", "")).trim();
        if (command.isEmpty()) throw new IllegalArgumentException("command 不能为空");
        int timeout = ((Number) args.getOrDefault("timeout", 20)).intValue();
        if (timeout < 1 || timeout > 120) throw new IllegalArgumentException("timeout 必须在 [1, 120] 范围内");
    }

    @Override
    public String execute(Pico pico, Map<String, Object> args) throws Exception {
        String command = String.valueOf(args.getOrDefault("command", "")).trim();
        int timeout = ((Number) args.getOrDefault("timeout", 20)).intValue();
        ProcessBuilder pb = new ProcessBuilder("bash", "-lc", command);
        pb.directory(pico.root().toFile());
        pb.environment().clear();
        pb.environment().putAll(pico.shellEnv());
        Process process = pb.start();
        boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("命令执行超时");
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        return ("退出码：" + process.exitValue() + "\nstdout:\n" + (stdout.isBlank() ? "(空)" : stdout) +
                "\nstderr:\n" + (stderr.isBlank() ? "(空)" : stderr)).trim();
    }
}
