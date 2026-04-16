# pico-java v1

`pico-java` 是 Python 版 `pico` 编码代理的 Java 重实现。

它不是逐行翻译版本。项目目标是在 Java 生态下保持相同的功能语义，同时用更符合 Java 风格的方式划分 CLI、运行时、会话、模型适配层、工具、运行记录和记忆层的职责边界。

## 项目状态

这个仓库目前已经是一个最小但可运行的本地编码代理，具备：

- 基于 Picocli 的 CLI 与 REPL 入口
- OpenAI-compatible、Anthropic-compatible、Ollama 三种模型适配层
- 会话保存与恢复
- 运行产物与追踪输出
- 支持工具调用的多步代理主循环
- 分层记忆与上下文管理
- 本地同进程 delegate / 子代理能力

它仍然是一个小型代理骨架，不是生产级编排平台。

## 架构说明

主要包结构如下：

- `com.picojava.cli`
  负责 CLI 参数解析、环境变量解析、REPL 启动
- `com.picojava.agent`
  负责主运行循环、prompt 构建、上下文管理、响应解析
- `com.picojava.model`
  负责模型提供方适配、配置解析、统一模型响应抽象
- `com.picojava.tool`
  负责工具接口、注册表、内置工具、delegate 工具
- `com.picojava.session`
  负责会话持久化与恢复逻辑
- `com.picojava.run`
  负责单次 run 的 task state、trace writer、report writer、子 run 关联
- `com.picojava.memory`
  负责分层记忆、最近工具结果存储、后续总结接口预留
- `com.picojava.workspace`
  负责工作区快照与 git/文档摘要

高层运行流程：

1. CLI 创建 `WorkspaceContext`、`SessionStore` 和 `ModelClient`。
2. `Pico.ask(...)` 启动一次 run，并创建 `TaskState`。
3. `AgentRunner` 通过 `ContextManager` 组装 prompt 上下文。
4. 模型提供方专属的 `ModelClient` 返回统一的 `ModelResponse`。
5. `ResponseParser` 把模型文本解析成以下三类结果之一：
   - 最终回答
   - 工具调用
   - 重试提示
6. 工具通过 `ToolRegistry` 执行，结果再回灌到历史 / 记忆。
7. run 产物写入 `.pico/runs/<runId>/`。
8. session 状态保存到 `.pico/sessions/<sessionId>.json`。

## 构建

环境要求：

- Java 17+
- Maven 3.9+

构建项目：

```bash
mvn package
```

运行测试：

```bash
mvn test
```

运行打包后的 CLI：

```bash
java -jar target/pico-java-0.1.0-jar-with-dependencies.jar --help
```

## CLI 用法

单次执行模式：

```bash
java -jar target/pico-java-0.1.0-jar-with-dependencies.jar \
  --cwd . \
  --provider openai \
  "检查仓库并总结入口点"
```

交互式 REPL：

```bash
java -jar target/pico-java-0.1.0-jar-with-dependencies.jar --cwd .
```

恢复已有会话：

```bash
java -jar target/pico-java-0.1.0-jar-with-dependencies.jar --resume latest
java -jar target/pico-java-0.1.0-jar-with-dependencies.jar --resume session-20260413-123456-abc123
```

重要 CLI 参数：

- `--cwd`
  workspace 目录
- `--provider`
  `openai`、`anthropic` 或 `ollama`
- `--model`
  显式指定模型
- `--base-url`
  provider API 基础地址
- `--host`
  Ollama host 简写参数
- `--api-key`
  显式指定 provider API key
- `--timeout`
  请求超时时间，单位秒
- `--approval`
  `ask`、`auto` 或 `never`
- `--resume`
  恢复指定会话 id，或者 `latest`
- `--max-steps`
  主 agent 的步数上限
- `--max-new-tokens`
  模型输出 token 上限
- `--secret-env-name`
  额外需要脱敏的环境变量名，会从 shell 环境和产物中隐藏

## 环境变量

通用：

- `PICO_SECRET_ENV_NAMES`
  额外的敏感环境变量名，使用逗号分隔
- `PICO_BASE_URL`
  模型提供方配置共用的兜底 base URL
- `PICO_MODEL_TIMEOUT_SECONDS`
  共用的兜底超时时间

OpenAI-compatible：

- `OPENAI_API_KEY`
- `OPENAI_API_TOKEN`
- `OPENAI_API_BASE`
- `OPENAI_BASE_URL`
- `OPENAI_MODEL`
- `OPENAI_TIMEOUT_SECONDS`

Anthropic-compatible：

- `ANTHROPIC_API_KEY`
- `ANTHROPIC_AUTH_TOKEN`
- `ANTHROPIC_API_BASE`
- `ANTHROPIC_BASE_URL`
- `ANTHROPIC_MODEL`
- `ANTHROPIC_TIMEOUT_SECONDS`
- `RIGHT_CODES_API_KEY`
  作为 Anthropic-compatible 路由时可接受的兜底 key

Ollama：

- `OLLAMA_HOST`
- `OLLAMA_BASE_URL`
- `OLLAMA_MODEL`
- `OLLAMA_TIMEOUT_SECONDS`

## 模型提供方配置

### OpenAI-compatible

```bash
export OPENAI_API_KEY="sk-..."
export OPENAI_API_BASE="https://api.openai.com/v1"
export OPENAI_MODEL="gpt-5.4"

java -jar target/pico-java-0.1.0-jar-with-dependencies.jar \
  --provider openai \
  "总结这个仓库"
```

### Anthropic-compatible

```bash
export ANTHROPIC_API_KEY="sk-ant-..."
export ANTHROPIC_API_BASE="https://api.anthropic.com/v1"
export ANTHROPIC_MODEL="claude-sonnet-4-6"

java -jar target/pico-java-0.1.0-jar-with-dependencies.jar \
  --provider anthropic \
  "阅读 README 并总结安装步骤"
```

### Ollama

```bash
ollama serve
ollama pull qwen3.5:4b

java -jar target/pico-java-0.1.0-jar-with-dependencies.jar \
  --provider ollama \
  --host http://127.0.0.1:11434 \
  --model qwen3.5:4b \
  "检查本地仓库"
```

## Session 与 Run 目录

代理会在工作区根目录写入状态：

```text
.pico/
  sessions/
    <sessionId>.json
  runs/
    <runId>/
      task_state.json
      trace.jsonl
      report.json
      children/
        <childRunId>.json
```

### Session 文件

session 文件会保存：

- 对话历史
- 分层记忆状态
- 审批策略、步数上限等运行配置
- 最近一次 run id
- 最近若干次 run 摘要

### Run 产物

每次 run 至少会保存：

- `task_state.json`
  该次 run 的完整执行状态
- `trace.jsonl`
  prompt 构建、模型调用、tool 调用、retry、结束等事件时间线
- `report.json`
  最终汇总报告

对于 delegate 子任务：

- child run 会生成自己独立的 `runId`
- child run 仍按普通 run 写入 `.pico/runs/<childRunId>/`
- parent run 会在 `children/<childRunId>.json` 下保留 child 关联记录

## 内置工具

当前内置工具：

- `list_files`
- `read_file`
- `search`
- `run_shell`
- `write_file`
- `patch_file`
- `delegate`

`delegate` 是本地同进程子代理：

- 与主代理运行在同一进程
- 不支持并发
- 不做分布式调度
- 子代理步数上限有边界
- delegate 深度有边界

## 最小可运行示例

创建一个工作区，并通过 Ollama 执行一次请求：

```bash
mkdir demo-repo
cd demo-repo
git init
printf "# Demo\n\nhello\n" > README.md

ollama serve
ollama pull qwen3.5:4b

java -jar ../target/pico-java-0.1.0-jar-with-dependencies.jar \
  --cwd . \
  --provider ollama \
  --host http://127.0.0.1:11434 \
  --model qwen3.5:4b \
  --approval never \
  "读取 README.md 并总结内容"
```

预期结果：

- 代理会创建新的会话
- 它可能会调用 `read_file`
- 最终返回总结结果
- `demo-repo` 下会生成 `.pico/sessions/` 和 `.pico/runs/`

delegate 示例：

```xml
<tool>{"name":"delegate","args":{"task":"检查 README.md 并总结安装步骤","max_steps":2}}</tool>
```

父子结果结构示例见 [docs/examples/delegate-tool.md](docs/examples/delegate-tool.md)。

## 已实现功能

- 支持单次执行模式和 REPL 模式的 CLI
- session 创建、保存、按 id 恢复、恢复 latest
- 按 task 生成 run 产物
- 每个 run step 都会产生日志 trace 事件
- 模型适配层支持：
  - 统一 `ModelResponse`
  - usage 与 stop reason 记录
  - timeout 与 retry 处理
- 响应解析支持：
  - JSON 工具调用
  - XML 工具调用
  - 最终回答
  - 重试提示
- 分层记忆与上下文管理器
- 规则式上下文压缩
- delegate / 子代理工具
- 审批策略接入
- 已覆盖 parser、session、workspace、tool registry、model adapter、主循环、run artifact、delegate 行为测试

## 尚未实现

- 并行子代理
- 分布式工作节点
- 基于模型总结的更强长期记忆蒸馏
- 后台任务调度
- 主循环中的 provider streaming
- 从 CLI/runtime 端到端暴露 prompt cache 控制
- 沙箱化 shell 执行
- 更细粒度的只读 delegate 工具子集
- 多 provider fallback 或 routing 策略

## 本地验证清单

1. 运行 `mvn test`。
2. 运行 `java -jar target/pico-java-0.1.0-jar-with-dependencies.jar --help`。
3. 在已配置好的 provider 上执行一次单次任务。
4. 检查 `.pico/sessions/` 和 `.pico/runs/`。
5. 使用 `--resume latest` 进行恢复。
6. 如果验证 delegate 行为，额外检查 `.pico/runs/` 下的父子 run 目录。
