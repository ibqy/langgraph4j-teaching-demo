# 与 Spring AI Alibaba Graph 对照

两套课程都强调真实图执行，但不是同一个依赖的不同封装。以下对照限定在 **LangGraph4j 1.9.5** 与配套课程使用的 **Spring AI Alibaba Graph 1.1.2.4-security-fix**，不推断后续版本。

## 定位与选择

| 关注点 | LangGraph4j 课程 | Alibaba Graph 课程 |
|---|---|---|
| 原生 API 包 | `org.bsc.langgraph4j` | `com.alibaba.cloud.ai.graph` |
| 状态类型 | `AgentState` 子类与 Channel schema | `OverAllState` 与键更新策略 |
| 图定义 | `StateGraph<LearningState>` | `StateGraph` |
| 模型示例 | LangChain4j ChatModel、工具调用协议 | Spring AI / DashScope |
| HTTP 宿主 | Spring Boot，非引擎必需条件 | Spring Boot |
| 默认运行 | 确定性节点、本地业务资料、离线模型 | 确定性节点与离线模型 |

已经采用 LangChain4j 的项目，可以优先评估 LangGraph4j 的状态与工具集成；已经采用 Spring AI Alibaba 的项目，可以先沿现有生态实现。选型应以所需持久化方案、业务恢复语义、模型供应商和团队维护能力为准，不仅比较类名。

## 相同概念，不同执行契约

### 输入与恢复

LangGraph4j 1.9.5 使用显式 `GraphInput`：

```java
app.invoke(GraphInput.args(input), config);
app.invoke(GraphInput.resume(), updatedConfig);
```

不要把 Alibaba 示例中的 `null` 输入与 `withResume()` 机械搬过来。LangGraph4j 仍有历史重载，但本项目以明确区分新执行与恢复的 API 为主。

### 状态合并

LangGraph4j 的 `Channels.appender` 会去除相等元素，`appenderWithDuplicate` 才保留重复。循环轨迹需要保留多次访问同一节点，不能仅凭“追加”这个词推断具体语义。

两边都应返回局部更新，不应该把整个最终状态反复喂给累加型 Reducer。概念相似不等于具体默认行为一致。

### 并行

LangGraph4j 原生并行分支应汇合到同一个节点；直接暴露的并行分支限制为单步，复杂分支可以封装成编译子图。同步动作需要显式并行执行器，不能只调用 `node_async` 就假定线程重叠。

对照测试应该验证分支是否都执行、汇合是否看到完整结果、实际动作是否重叠；不要要求两个并行分支具有固定完成顺序。

### 检查点保留

LangGraph4j `CompileConfig.releaseThread` 默认开启。本课程显式关闭，以便完成后仍能查阅检查点历史。`MemorySaver` 只存在于当前进程；是否持久化，取决于 saver 实现，不取决于“支持 checkpoint”这一标签。

### 流式输出

LangGraph4j 使用异步生成器，不是 Reactor `Flux` 原生契约。配套 Alibaba 项目曾针对其固定版本的背压行为加过缓冲；那不是 LangGraph4j 的通用修复，本项目不照搬。

## 推荐对照实验

1. 分别运行两边的 demo03，观察数值累加和列表追加。
2. 运行 demo06，只断言两个分支结果都到达，不比较竞态输出顺序。
3. 运行 demo10，记录暂停位置，拒绝一次、同意一次。
4. 运行 demo17 的高金额请求，连续完成两级审核，确认没有重复前序副作用。
5. 对照 demo11 的节点流，区分传输协议和引擎事件。

## 参考依据

- [LangGraph4j 1.9.0 源码](https://github.com/langgraph4j/langgraph4j/tree/v1.9.5)
- [原生并行分支限制](https://github.com/langgraph4j/langgraph4j/blob/v1.9.5/src/site/mkdocs/core/parallel-branch.md)
- [原生子图语义](https://github.com/langgraph4j/langgraph4j/blob/v1.9.5/src/site/mkdocs/core/subgraph.md)
- [配套 Spring AI Alibaba Graph 项目](https://github.com/ibqy/spring-ai-alibaba-graph-demo)
