# LangGraph4j 学习实战

18 个原生 LangGraph4j Demo：从状态、路由与并行，到检查点恢复、模型工具与业务工作流。**基础 6 课 + 高阶 9 课 + 实战 3 课**，附完整中文逐课文档和独立学习网站。

- [在线学习网站](https://ibqy.github.io/langgraph4j-teaching-demo/)
- [开始学习](docs/guide/getting-started.md)
- [运行接口](docs/guide/runtime.md)
- [与 Spring AI Alibaba Graph 对照](docs/guide/comparison.md)
- [能力与边界](docs/guide/boundaries.md)

## 技术栈与真实边界

| 组件 | 版本 / 职责 |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.8，应用宿主与本地 HTTP 接口 |
| LangGraph4j | 1.9.0，真实 StateGraph 编排引擎 |
| LangChain4j | 1.20.0，模型消息与工具协议 |
| VitePress | 1.6.4，静态中文文档站 |

默认不需要 API Key。01—11、13—18 课使用确定性 Java 节点；第 12 课使用明确标识的离线 ChatModel，经真实 LangChain4j 消息及 LC4jToolService 演示工具闭环。按需配置真实 OpenAI 兼容模型，不将离线回答冒充大模型推理。

工单政策、研究资料和支付均为教学模拟。MemorySaver、执行记录与幂等记录只在当前进程或执行状态中保留，不提供生产级持久化、认证、财务结算或跨进程幂等保证。GitHub Pages **只部署文档**，不托管 Java 后端。

## 快速运行

需要完整 JDK 21 和 Maven 3.6.3+；请用 `mvn -version` 确认 Maven 使用的 Java。

```bash
git clone https://github.com/ibqy/langgraph4j-teaching-demo.git
cd langgraph4j-teaching-demo
mvn verify
mvn spring-boot:run
```

另开终端，服务只监听本机 `127.0.0.1:8099`：

```bash
curl http://127.0.0.1:8099/api/demos
curl -X POST http://127.0.0.1:8099/api/demos/demo01/run \
  -H 'Content-Type: application/json' -d '{"input":{"name":"小明"}}'
```

结果应包含 `status=COMPLETED` 和 `state.greeting=你好，小明！`。用响应中的 executionId 查询结果与检查点：

```bash
EXECUTION_ID='替换为实际响应中的executionId'
curl "http://127.0.0.1:8099/api/executions/$EXECUTION_ID"
curl "http://127.0.0.1:8099/api/executions/$EXECUTION_ID/history"
```

默认不调用外部模型不等于首次构建不联网；Maven 与 npm 首次安装需要下载依赖。

## 课程地图

### 基础：把图跑起来

| 课 | 内容 | 核心观察 |
|---|---|---|
| [01](docs/lessons/01-hello.md) | Hello Graph | START → 节点 → END |
| [02](docs/lessons/02-sequence.md) | 顺序编排 | 提纲、正文、发布的状态依赖 |
| [03](docs/lessons/03-state.md) | 状态与 Reducer | 增量累加、去重追加、重复轨迹、替换 |
| [04](docs/lessons/04-routing.md) | 条件路由 | 分数决定 pass/remedial |
| [05](docs/lessons/05-loop.md) | 循环与退出 | 可达终止条件与边界 |
| [06](docs/lessons/06-parallel.md) | 并行与汇合 | 显式执行器与真实重叠执行 |

### 高阶：让执行可控

| 课 | 内容 | 核心观察 |
|---|---|---|
| [07](docs/lessons/07-subgraph.md) | 子图组合 | compiled subgraph 与父图数据流 |
| [08](docs/lessons/08-checkpoint.md) | 检查点与历史 | MemorySaver 与历史快照 |
| [09](docs/lessons/09-isolation.md) | 线程隔离 | 同图不同 threadId 的状态隔离 |
| [10](docs/lessons/10-human-review.md) | 人工介入 | interruptBefore、更新与恢复 |
| [11](docs/lessons/11-streaming.md) | 节点流与 SSE | 原生异步生成器，不冒充 Token 流 |
| [12](docs/lessons/12-model.md) | 模型与工具 | ChatModel → 工具 → 模型闭环 |
| [13](docs/lessons/13-retry.md) | 有界重试 | 失败计数、重试边界与耗尽路径 |
| [14](docs/lessons/14-idempotency.md) | 幂等与副作用 | 重复操作只改变一次余额 |
| [15](docs/lessons/15-observability.md) | 可观测性 | 节点动作包装、轨迹与真实计时 |

### 实战：把机制串成闭环

| 课 | 内容 | 核心观察 |
|---|---|---|
| [16](docs/lessons/16-ticket.md) | 工单处理 | 分类、知识选择、回复草稿、人工审核 |
| [17](docs/lessons/17-expense.md) | 多级费用审批 | 经理与财务分级、最新检查点、拒绝分支 |
| [18](docs/lessons/18-research.md) | 研究报告 | 并行资料、来源标识、综合、审核发布 |

## 体验人工审批与节点流

```bash
curl -X POST http://127.0.0.1:8099/api/demos/demo17/run \
  -H 'Content-Type: application/json' -d '{"input":{"amount":1500}}'
```

金额超过 1000 需经理和财务两次审批。将本次 executionId 填入变量，每次先观察响应再继续：

```bash
EXECUTION_ID='替换为上述响应中的executionId'
curl -X POST "http://127.0.0.1:8099/api/executions/$EXECUTION_ID/resume" \
  -H 'Content-Type: application/json' -d '{"approved":true}'
```

第一次批准后仍为 `WAITING_REVIEW`，第二次批准后才完成。任一级 `false` 都会拒绝；`COMPLETED` 只表示图正常结束，不等于业务批准。

```bash
curl -N http://127.0.0.1:8099/api/demos/demo11/stream
curl http://127.0.0.1:8099/api/demos/demo06/graph
```

SSE 以默认输入创建执行，不是既有执行的只读订阅。节点级输出、LLM Token 流、检查点历史是三个不同概念。

## 可选真实模型

仅显式设置 `TEACHING_MODEL_PROVIDER=openai` 才创建远程客户端，还需以下环境变量：

| 变量 | 说明 |
|---|---|
| `TEACHING_MODEL_API_KEY` | 自行配置的密钥，不写入源码与 Git |
| `TEACHING_MODEL_BASE_URL` | 服务商提供的 OpenAI 兼容基础地址 |
| `TEACHING_MODEL_NAME` | 支持工具调用的模型名称 |

配置后重启服务，执行 demo12。未配置时保持 `offline`；远程模型输出不保证与离线样例逐字相同。详见[快速开始](docs/guide/getting-started.md)和[模型课](docs/lessons/12-model.md)。

## 本地文档与部署

Node.js 20.19.0+：

```bash
npm ci
npm run docs:dev
npm run docs:build
npm run docs:preview
```

项目站基础路径为 `/langgraph4j-teaching-demo/`。仓库工作流在 main 推送和 PR 时运行 Java 验证与文档构建，仅 main 非 PR 构建发布至 GitHub Pages；Pages 的构建来源需设置为 GitHub Actions。

## 目录

```text
src/main/java/com/xb/langgraph/
  GraphDemo.java          课程统一契约
  LearningState.java      原生状态与 Channel schema
  demo01/ ... demo18/     每课的真实图定义
  web/                   本机运行、审核、历史与 SSE
src/test/java/           原生课程与运行接口测试
docs/
  guide/                 入门、概念、接口、对照与边界
  lessons/               18 课逐步讲解
  .vitepress/            网站配置与交互首页
```

建议每课先跑默认样例，再主动改输入、观察分支和历史，最后阅读对应测试。完整测试不依赖真实模型服务；并行测试通过同步机制证明实际重叠，不靠固定睡眠或耗时阈值猜测。

## License

[MIT](LICENSE)
