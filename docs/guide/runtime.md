# 运行接口：观察一次执行的完整生命周期

Java 服务默认地址为 `http://127.0.0.1:8099`。这些接口供本机学习使用，没有身份认证和多租户权限控制，不应直接暴露到公网。在线文档站不会代你调用本机接口。

## 接口一览

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/demos` | 查看 18 个课程及默认输入 |
| GET | `/api/demos/{demoId}/graph` | 获取原生图定义生成的 Mermaid 文本 |
| POST | `/api/demos/{demoId}/run` | 新建执行，运行到结束或审核暂停 |
| GET | `/api/executions/{executionId}` | 查看当前执行结果 |
| GET | `/api/executions/{executionId}/history` | 查看当前执行的检查点历史 |
| POST | `/api/executions/{executionId}/resume` | 提交当前审核决定并恢复 |
| GET | `/api/demos/{demoId}/stream` | 使用该课默认输入，以 SSE 观察节点输出 |

`demoId` 为 `demo01` 到 `demo18`，不是文档页的 slug。`executionId` 由服务生成，需从实际响应取得。

## 状态分为运行器层与业务层

| 运行器 status | 含义 | 接下来做什么 |
|---|---|---|
| `RUNNING` | 图正在执行 | 等待结果；不要并发恢复 |
| `WAITING_REVIEW` | 停在声明的审核节点之前 | 读取当前状态并提交布尔决定 |
| `COMPLETED` | 图正常结束 | 检查业务状态与结果 |
| `FAILED` | 图执行未正常完成 | 排查参数、节点异常或执行限制 |

例如第 17 课拒绝报销时，运行器可以是 `COMPLETED`，但 `state.status` 是 `rejected`、`state.paid` 是 `false`。不能只用 HTTP 200 或 `COMPLETED` 判断业务成功。

## 1. 发现课程及默认输入

```bash
curl http://127.0.0.1:8099/api/demos
curl http://127.0.0.1:8099/api/demos/demo06/graph
```

目录中的 `sampleInput` 是输入契约。运行器会合并默认值与显式输入；只接受该课公开的字段，不允许通过额外字段偷偷写入图的审核结果、余额或内部状态。

图接口来自课程 `StateGraph` 的原生表示，不是单独手画的执行替代品。Mermaid 表示的是静态拓扑，不代表该次请求实际走过所有分支。

## 2. 创建并查询执行

```bash
curl -X POST http://127.0.0.1:8099/api/demos/demo04/run \
  -H 'Content-Type: application/json' -d '{"input":{"score":85}}'
```

响应中的关键字段：

- `executionId`：当前进程中的执行记录标识。
- `demoId`：对应课程。
- `status`：运行器状态。
- `state`：最新的图状态。
- `visitedNodes`：本次执行已观察到的节点轨迹。

把实际返回的 ID 保存为变量再查询：

```bash
EXECUTION_ID='替换为实际响应中的executionId'
curl "http://127.0.0.1:8099/api/executions/$EXECUTION_ID"
curl "http://127.0.0.1:8099/api/executions/$EXECUTION_ID/history"
```

HTTP 检查点历史按旧到新排列，便于顺序阅读；原生 `getStateHistory` 返回新到旧，运行器做了反转。它记录状态快照与执行位置，区别于应用日志；`visitedNodes` 也不是模型 Token 记录。并行节点结果的遍历顺序不能被当作业务先后依赖。

运行中查询历史可能产生不一致视图，因此运行器拒绝此类冲突请求。运行记录最多保留 100 条，优先淘汰终态；正在执行和等待审核的记录不会为新请求被直接抛弃。记录满且没有可淘汰项时，新执行会被拒绝。

## 3. 单级审核：批准和拒绝

```bash
curl -X POST http://127.0.0.1:8099/api/demos/demo10/run \
  -H 'Content-Type: application/json' -d '{"input":{"content":"待审核文章"}}'
```

看到 `WAITING_REVIEW` 后，把响应 ID 填入变量：

```bash
EXECUTION_ID='替换为实际响应中的executionId'
curl -X POST "http://127.0.0.1:8099/api/executions/$EXECUTION_ID/resume" \
  -H 'Content-Type: application/json' -d '{"approved":true}'
```

要体验拒绝，创建另一条新执行，再把决定改为 `false`。已结束的执行不能再次恢复；该情况属于状态冲突，不会悄悄重跑。

恢复输入故意保持很窄：只有 `approved` 布尔值。当前待审核节点和对应的状态字段由服务判断，客户端不能指定 `nextNode`、`asNode` 或提交任意状态更新。

## 4. 两级费用审批

第 17 课 `amount > 1000` 时需两次审核。以默认 1500 为例：

```bash
curl -X POST http://127.0.0.1:8099/api/demos/demo17/run \
  -H 'Content-Type: application/json' -d '{"input":{"amount":1500}}'
```

1. 初次暂停：业务状态为 `pending_manager`。
2. 使用此 executionId 提交 `{"approved":true}`：经理批准，仍然 `WAITING_REVIEW`，业务状态为 `pending_finance`。
3. **再次使用同一个 executionId** 提交 `{"approved":true}`：财务批准，执行结束，`state.status=paid`、`state.paid=true`。
4. 任一级传 `false` 都会走拒绝路径；金额不超过 1000 时不经过财务节点。

两次恢复都提交相同 JSON，但服务依据**最新**检查点把决定应用到不同节点。第二次请求不是重复第一次审批。本例支付为状态中的离线模拟，没有外部转账。

## 5. SSE：边执行边观察节点

```bash
curl -N http://127.0.0.1:8099/api/demos/demo11/stream
```

SSE 响应使用 `text/event-stream`，包含开始、节点和最终状态事件。观察 `outline → draft → publish`，并以最终事件中的运行状态判断结束或暂停。第 10、16—18 课可能暂停等待审核，而不是一直保持 HTTP 连接等待人操作。

流式入口使用课程默认输入；需要自定义参数时使用 `/run`。这个 GET 会创建执行，因此不是只读状态订阅，不要让自动页面预取反复访问它。

浏览器端可以使用原生 `EventSource`，但本项目不提供在线执行前端。`curl -N` 只是避免客户端缓冲，不会把节点事件变成 Token 级输出。

连接超时或客户端断开时，运行器需要停止消费并处理生成器取消；已发生的外部副作用并不会因此自动撤销。对真实供应商而言，连接结束也不保证对方已经停止请求处理。

## 生命周期为何这样设计

每个 executionId 对应独立的图实例、MemorySaver 与 threadId 配置，避免不同 HTTP 执行共享内部状态。第 09 课另外通过测试展示**同一个编译图**如何使用不同 threadId 隔离检查点，两者是不同层次的隔离。

编译时保留已完成的检查点，并把课程声明的 reviewNodes 注册为 `interruptBefore`。恢复时使用当前 threadId 的最新位置，注入限定的决定，调用原生 `GraphInput.resume()`；新执行则使用 `GraphInput.args(...)`。

线程 ID 和执行 ID 都不具有身份认证作用；教学运行器不包含审批人的权限检查、审计签名、跨实例锁或持久化任务队列。生产化前先阅读[能力与边界](/guide/boundaries)。
