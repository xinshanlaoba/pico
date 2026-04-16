# Delegate Tool 示例

模型输出示例：

```xml
<tool>{"name":"delegate","args":{"task":"检查 README.md 并总结关键安装步骤","max_steps":2}}</tool>
```

返回给父 agent 的 tool 结果示例：

```text
delegate_result:
child_run_id: run-20260413-abc123
status: completed
final_answer:
子 agent 输出的 README 摘要
```

父 agent 可以在下一步继续使用这段摘要，并推进自己的最终回答。
