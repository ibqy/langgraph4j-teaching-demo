# 常见问题：先判断是哪一层失败

把问题分成四层：**构建环境、HTTP 运行器、图的控制流、外部模型**。不要通过反复换配置掩盖根因；先保存请求、响应、异常类型和发生节点，再逐层缩小范围。

## 编译提示不支持 release 21

检查 `mvn -version` 的 Java version 和 Java home，而不只看 `java -version`。Maven 必须使用 JDK 21。IDE 中也检查 Maven Runner 的 JRE；如果 JDK 目录没有 `bin/java`，它不是完整的可运行 JDK。

Bash / Git Bash 可在当前终端指定完整 JDK：

```bash
export JAVA_HOME='/你的完整JDK21目录'
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version
mvn verify
```

## 依赖下载失败，但示例不是离线的吗？

离线指默认课程不调用远程模型和业务系统。首次 Maven/npm 安装仍需要下载公开依赖。检查代理、仓库镜像和本机证书，不要关闭 TLS 校验，也不要把访问凭据提交到项目。依赖下载完成后可运行 `mvn -o test`。

## 服务启动了，但 8099 访问不到

默认绑定 `127.0.0.1`，只能从运行 Java 的本机访问。先确认启动日志没有端口占用或模型配置异常。不要为了方便直接绑定 `0.0.0.0`；示例没有公网认证与授权设计。

若端口被占用，可以显式启动到另一个端口，并同步修改 curl 地址：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8100
```

## 请求 400：JSON 格式或输入字段错误

- 请求头应包含 `Content-Type: application/json`。
- 运行请求最外层为 `{"input":{...}}`，不是直接发送样例字段。
- 字段名从 `GET /api/demos` 的 `sampleInput` 获取。
- 数字传 JSON 整数，例如 `85`，不是 `"85"` 或 `85.5`。
- 恢复传 `{"approved":true}`，不是 `"true"`、`1` 或任意状态 Map。
- 未声明字段会被拒绝，不会自动成为可以控制图的变量。

图内部也会验证业务范围。HTTP 请求被接收不代表业务一定成功；读取运行器状态与业务状态，区分参数错误、执行异常和正常拒绝。

## 审核一直停在 WAITING_REVIEW

第 10、16、18 课会在 `review` 前暂停。第 17 课金额超过 1000 时需要经理和财务两次审批，第一次恢复后仍为 `WAITING_REVIEW` 是预期行为。

每次读取最新响应，再决定是否需要下一次恢复。不要重复发起 `/run`，那会创建另一次执行；也不要把两个浏览器标签里不同的 `executionId` 混用。

## 第二级审核用了第一级的旧状态

源码调试时，不要长期持有带旧 `checkpointId` 的 `RunnableConfig`。每次从同一 `threadId` 的最新位置取得状态后更新，使用恢复语义继续。`updateState` 的 `asNode` 不是“下一节点名”，不理解其含义时不要填写。

项目的 HTTP 恢复接口只接受布尔决定，由运行器根据当前待审核节点映射字段；不允许客户端直接写 `managerApproved`、`financeApproved` 或 `status`。

## 审核拒绝，为什么运行状态是 COMPLETED？

`COMPLETED` 表示图按定义走到了 END。`state.status=rejected` 表示业务决定为拒绝。拒绝是合法的业务分支，不等于 Java 异常。相同地，第 13 课耗尽重试会得到业务状态 `exhausted`，而不是伪装成成功结果。

## 服务重启后 executionId 找不到了

执行记录与 `MemorySaver` 检查点都在当前进程内。重启、结束进程或记录被容量淘汰后，原 ID 不再可用。示例不是持久化任务平台，不提供跨进程恢复保证。需要生产持久化时，还要同时考虑状态序列化、版本兼容、租户隔离和数据清理。

## Reducer 累加结果翻倍或轨迹丢失

累加节点返回本次增量，不返回累计总数。追加节点返回新元素，不返回整份历史。

`Channels.appender` 会去掉相等元素。如果你想观察某节点重复执行两次，请使用保留重复的通道或不同事件 ID；不要把去重后的列表长度当执行次数。见[第 03 课](/lessons/03-state)。

## node_async 后为什么还是串行？

`node_async` 不自动把同步函数派发到线程池。第 06、18 课把并行执行器绑定到扇出源节点 `dispatch`，并在测试中用同步闩锁证明两个分支确实重叠，而不是凭总耗时猜测。

直接并行分支有拓扑限制。多步分支应封装成编译子图，不要随意把一长串节点挂到并行支路。见[第 06 课](/lessons/06-parallel)和[第 07 课](/lessons/07-subgraph)。

## 模型消息无法保存检查点

LangChain4j 的 `ChatMessage` 不是普通可序列化业务对象。第 12 课使用官方 `LC4jStateSerializer` 构造图。只复制其他课程的默认构造器，可能在保存消息状态时失败。HTTP JSON 展示和检查点序列化是两个不同问题。

## 真实模型模式启动失败或工具调用失败

四个环境变量必须配套：`TEACHING_MODEL_PROVIDER=openai`、`TEACHING_MODEL_API_KEY`、`TEACHING_MODEL_BASE_URL`、`TEACHING_MODEL_NAME`。兼容地址应是供应商要求的 API 基础路径，不是聊天网页地址。

- 模型必须支持工具调用；普通文本生成不等于工具协议兼容。
- 本项目不证明任意兼容供应商都可用，需要针对实际供应商验证。
- 供应商超时为 30 秒，客户端自动重试关闭；图中的模型工具循环另有限制。
- 密钥通过环境变量提供，日志不要打印请求头和完整模型配置。
- 默认离线算术结果可复现；远程回答文字不保证与离线样例完全相同。

## SSE 没有逐字输出

本项目 SSE 推送**节点完成事件**，不是 token。`curl` 使用 `-N` 禁止输出缓冲：

```bash
curl -N http://127.0.0.1:8099/api/demos/demo11/stream
```

若在代理后部署，代理也可能缓冲响应；但该示例默认仅本地运行。连接断开不等于已经撤销外部副作用，模型请求也可能有自己的生命周期。

## 网站构建失败或页面 404

在项目根目录执行 `npm ci` 后再 `npm run docs:build`。项目站基础路径是 `/langgraph4j-teaching-demo/`；不能把资源路径假定为域名根目录。查看构建日志中第一个错误，优先修复缺失链接、错误源码引用和 Vue 模板语法。

Markdown 中含 `<`、泛型和模板占位符时请使用代码块或行内代码，避免被当作 HTML/Vue。GitHub Pages 不运行 Java，在线页面正常并不代表本机 8099 已启动。

## 如何提交可复现的问题

请提供：JDK/Maven 版本、课程 ID、脱敏请求、响应状态、预期行为、实际行为与最小复现步骤。不要提交 API Key、真实客户数据或财务信息。先用默认离线模式复现，有助于区分图执行问题和供应商问题。
