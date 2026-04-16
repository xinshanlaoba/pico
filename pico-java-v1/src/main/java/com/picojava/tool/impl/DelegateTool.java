package com.picojava.tool.impl;

import com.picojava.agent.Pico;
import com.picojava.run.TaskState;
import com.picojava.tool.BaseTool;

import java.util.Map;

public class DelegateTool extends BaseTool {
    private static final int DEFAULT_MAX_STEPS = 3;

    public DelegateTool() {
        super("delegate", false, "{task:str,max_steps:int=3}",
                "请求一个有步数边界的子 agent 调查问题，并返回简洁结果。");
    }

    @Override
    public void validate(Pico pico, Map<String, Object> args) {
        String task = String.valueOf(args.getOrDefault("task", "")).trim();
        if (task.isEmpty()) {
            throw new IllegalArgumentException("task 不能为空");
        }
        int maxSteps = ((Number) args.getOrDefault("max_steps", DEFAULT_MAX_STEPS)).intValue();
        if (maxSteps < 1 || maxSteps > pico.maxSteps()) {
            throw new IllegalArgumentException("max_steps 必须在 [1, " + pico.maxSteps() + "] 范围内");
        }
        if (!pico.canDelegate()) {
            throw new IllegalArgumentException("delegate 深度已超过上限");
        }
    }

    @Override
    public String execute(Pico pico, Map<String, Object> args) throws Exception {
        String task = String.valueOf(args.get("task")).trim();
        int maxSteps = ((Number) args.getOrDefault("max_steps", DEFAULT_MAX_STEPS)).intValue();
        TaskState.ChildRun childRun = pico.delegateTask(task, maxSteps);
        StringBuilder sb = new StringBuilder();
        sb.append("delegate 结果：\n");
        sb.append("子 run id：").append(childRun.getRunId()).append('\n');
        sb.append("状态：").append(childRun.getStatus()).append('\n');
        if (!childRun.getFinalAnswer().isBlank()) {
            sb.append("最终答案：\n").append(childRun.getFinalAnswer());
        } else if (!childRun.getError().isBlank()) {
            sb.append("错误：\n").append(childRun.getError());
        } else {
            sb.append("最终答案：\n(空)");
        }
        return sb.toString().trim();
    }
}
