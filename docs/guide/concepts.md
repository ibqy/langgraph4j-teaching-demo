# 核心概念：图执行的六个层次

先区分三个角色：**LangGraph4j 管编排，LangChain4j 管模型与工具协议，Spring Boot 管应用启动与 HTTP 接口。** 不调用大模型，也可以完整学习图的执行机制。

## 1. State：一次执行的共享上下文

`LearningState` 继承 `AgentState`。输入、节点结果和业务决定都通过状态传递。`state.data()` 是只读视图；节点读取已有数据并返回局部更新，而不是直接往视图写入值。

`inputText(key)` 在外部文本输入边界检查非空与 2000 字符上限；`text(key)` 读取内部生成文本，不把输入长度上限错误地套到提纲、正文或报告上。

```java
var workflow = new StateGraph<>(LearningState.SCHEMA, LearningState::new)
    .addNode("greet", node_async(state -> Map.of("greeting", "你好")))
    .addEdge(START, "greet")
    .addEdge("greet", END);
```

这里的 `Map.of("greeting", "你好")` 是更新量，不是完整状态。更新后原先的输入仍然存在。

## 2. Channel 与 Reducer：同一个键怎样更新

| 配置 | 含义 | 常见用途 |
|---|---|---|
| 未声明 Channel 的字段 | 后值替换前值 | 最终结论、当前步骤 |
| `Channels.base(defaultProvider)` | 有默认值的替换 | 初始标记 |
| `Channels.base(reducer, defaultProvider)` | 按函数合并 | 累计计数 |
| `Channels.appender(...)` | 追加并去除相等元素 | 去重来源列表 |
| `Channels.appenderWithDuplicate(...)` | 追加，保留重复 | 循环中的执行轨迹 |

例如累加器当前为 2，节点要“增加 1”，必须返回 1，而不是返回 3；否则 Reducer 会计算 2 + 3。追加器也是如此：只返回新元素，不要再次返回整个历史列表。

同一字段若同时由多个并行节点写入，必须先想清楚合并规则。最容易解释的做法是分支各写独立字段，汇合节点统一读取。

## 3. Node 与 Edge：做事与选择下一步

节点负责业务计算；边负责控制流。条件边应该读状态做决定，不应顺带扣费、发消息或调用外部业务。

- 普通边：固定后继，适合顺序流水线。
- 条件边：路由函数返回逻辑标签，再映射到目标节点。
- 回边：再次进入已执行节点，需要可达到的终止条件。
- 并行边：同一节点扇出，再汇入共同节点。

`node_async` 是同步动作的异步接口适配器，不是“自动多线程”开关。并行示例会把执行器显式绑定到扇出源节点。

## 4. StateGraph 与 CompiledGraph

`StateGraph` 是定义：节点、边、状态结构。`compile()` 验证并构造可执行图；`CompiledGraph` 承担调用、流式遍历、检查点与恢复。

```java
var app = workflow.compile();
var config = RunnableConfig.builder().threadId("lesson-001").build();
var result = app.invoke(GraphInput.args(Map.of("name", "小北")), config);
```

1.9.0 推荐明确使用 `GraphInput`，避免依赖即将移除的 Map 重载。`GraphInput.noArgs()` 表示新执行但无显式参数，`GraphInput.resume()` 表示恢复；两者不可互换。

## 5. Checkpoint 与 threadId

检查点保存某个步骤后的状态及下一步位置。`threadId` 是检查点存储中的会话键，不是 Java 线程 ID，也不是用户身份凭证。

```java
var app = workflow.compile(CompileConfig.builder()
    .checkpointSaver(new MemorySaver())
    .releaseThread(false)
    .interruptBefore("review")
    .build());
```

- `interruptBefore("review")` 停在审核节点执行之前。
- `releaseThread(false)` 保留已完成执行的活跃检查点供教学查询。
- `getStateHistory(config)` 按新到旧返回快照。
- `updateState(config, updates)` 仍然遵守 Reducer 规则，并返回新的配置。
- `GraphInput.resume()` 用于恢复，不应重新提交初始输入。

多级审核应每次从当前 threadId 的最新状态恢复。历史快照配置是“指定旧位置”，不是“总是最新”的引用。第三个参数 `asNode` 表示把更新当作该节点的输出，可能跳过该节点；不要把它误当成“下一步执行谁”。

## 6. Stream：节点事件，而不是自动的 Token 流

`CompiledGraph.stream(...).stream()` 遍历原生异步生成器的输出。节点更新、图起止事件可被转成 SSE；模型 Token 流是另一种接口与协议，不能因为 HTTP 使用 SSE 就宣称实现了 Token 级流式生成。

流式消费者必须处理失败、客户端断开和资源释放。模型调用还需要供应商超时，HTTP 连接结束不保证底层外部请求已停止。

## 学习检查

1. 一个节点返回完整累计列表，为什么在追加 Reducer 下可能重复？
2. 把 `threadId` 换掉，再发恢复请求，为什么找不到原来的检查点？
3. 设置了 `node_async`，为什么两个同步耗时节点还可能串行？
4. 审核被拒绝但图正常走到 END，应算业务拒绝还是执行失败？

带着这些问题阅读[状态课](/lessons/03-state)、[并行课](/lessons/06-parallel)和[人工介入课](/lessons/10-human-review)。
