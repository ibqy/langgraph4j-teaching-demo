# 开始学习：先把一张真实的图跑起来

本项目使用 **LangGraph4j 1.9.0 + Java 21 + Spring Boot 3.5.8**。前 11 课完全不依赖模型，第 12 课默认也使用离线模型实现，因此第一次运行不需要申请 API Key。

## 环境准备

| 工具 | 要求 | 用途 |
|---|---|---|
| JDK | 21 | 编译和运行 Java 示例 |
| Maven | 3.6.3 或更新 | 下载依赖、测试与打包 |
| Node.js | 20.19.0 或更新 | 仅本地预览文档需要 |
| npm | 与 Node.js 配套 | 安装 VitePress 依赖 |
| curl | 支持 HTTP 请求 | 调用本地运行接口 |

先执行：

```bash
java -version
mvn -version
```

两处显示的 Java 都应为 21。IDE 的项目 SDK、Maven Runner 和终端的 `JAVA_HOME` 是不同设置；只改其中一个，未必会影响另一个。

## 获取源码并验证

```bash
git clone https://github.com/ibqy/langgraph4j-teaching-demo.git
cd langgraph4j-teaching-demo
mvn verify
```

首次执行会下载 Maven 依赖，需要联网。“默认无 Key”不表示首次构建不需要下载依赖。后续依赖齐全时，可以使用 `mvn -o test` 进行离线测试。

测试不仅检查 Spring 是否能启动，还覆盖 Reducer、真实并行重叠、历史检查点、线程隔离、审批分支与模型工具消息协议。

## 启动 Java 服务

```bash
mvn spring-boot:run
```

服务默认只监听 `127.0.0.1:8099`。另开终端列出课程：

```bash
curl http://127.0.0.1:8099/api/demos
```

运行第一课：

```bash
curl -X POST http://127.0.0.1:8099/api/demos/demo01/run \
  -H 'Content-Type: application/json' \
  -d '{"input":{"name":"小明"}}'
```

响应包含运行器的 `status`、唯一 `executionId` 和图的 `state`。应看到以下关键字段，其余状态字段可忽略：

```json
{
  "demoId": "demo01",
  "status": "COMPLETED",
  "state": { "name": "小明", "greeting": "你好，小明！" }
}
```

这是结果摘录，不是完整响应。`executionId` 每次不同；后续查询与恢复必须使用本次响应返回的值。

Windows PowerShell 用户可使用 `curl.exe`，避免旧版 PowerShell 的 `curl` 别名。上面的续行和单引号示例采用 Bash / Git Bash 语法；PowerShell 也可以直接使用对象：

```powershell
$body = @{ input = @{ name = '小明' } } | ConvertTo-Json
Invoke-RestMethod -Uri 'http://127.0.0.1:8099/api/demos/demo01/run' `
  -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

## 第一次体验暂停与恢复

运行人工审核课：

```bash
curl -X POST http://127.0.0.1:8099/api/demos/demo10/run \
  -H 'Content-Type: application/json' -d '{"input":{"content":"待审核文章"}}'
```

返回 `WAITING_REVIEW` 是正常暂停，不是错误。复制响应的 `executionId`，把下面的 `实际执行ID` 替换掉：

```bash
curl -X POST http://127.0.0.1:8099/api/executions/实际执行ID/resume \
  -H 'Content-Type: application/json' -d '{"approved":true}'
```

这次请求恢复原来的检查点，不创建新执行。若传 `false`，图也可以正常结束，但业务状态为拒绝。详见[人工介入课](/lessons/10-human-review)和[运行接口](/guide/runtime)。

## 本地阅读文档

Java 与文档开发服务器互相独立。只启动 Java 不会出现网站，只启动 VitePress 也不会启动 Java。

```bash
npm ci
npm run docs:dev
```

打开终端显示的地址，项目站路径为 `/langgraph4j-teaching-demo/`。生产静态构建：

```bash
npm run docs:build
npm run docs:preview
```

在线文档：[LangGraph4j 学习实战](https://ibqy.github.io/langgraph4j-teaching-demo/)。GitHub Pages 只托管静态网页，不接收你的运行请求，也不保存审批状态。

## 学习顺序

1. **01—06 基础**：把图跑起来，理解节点返回局部更新、路由、循环与并行合并。
2. **07—11 执行控制**：组合子图，检查状态历史，区分 threadId，并亲自暂停和恢复。
3. **12—15 工程机制**：接模型与工具，限定重试，控制副作用，观察真实执行计时。
4. **16—18 业务闭环**：工单、费用审批、研究报告。先跑通过，再主动制造拒绝和失败。

每课建议用同一套动作：读输入与拓扑 → 执行默认样例 → 查看状态与历史 → 修改一个条件 → 对照源码和测试解释变化。

## 按需开启真实模型

仅第 12 课需要模型协议。默认 `offline` 通过真实 LangChain4j 消息和工具服务完成确定性算术闭环；它不是外部大模型，也不能用来评估模型能力。

若已有支持工具调用的 OpenAI 兼容服务，设置环境变量后重启 Java：

```bash
export TEACHING_MODEL_PROVIDER=openai
export TEACHING_MODEL_BASE_URL='替换为服务商的兼容API基础地址'
export TEACHING_MODEL_NAME='替换为支持工具调用的模型名称'
read -s -p 'API Key: ' TEACHING_MODEL_API_KEY
export TEACHING_MODEL_API_KEY
mvn spring-boot:run
```

不要把占位文字原样当地址使用。远程服务必须使用 HTTPS；只有本机回环开发地址允许 HTTP。API Key 不要写进源码、文档、前端或 Git。环境变量在新进程启动时读取；配置不完整会启动失败，不会偷偷回退到离线模式。客户端超时为 30 秒，供应商重试关闭，模型工具循环另有轮数上限。

恢复离线模式时，将 `TEACHING_MODEL_PROVIDER` 设为 `offline` 并重启。具体消息流和序列化说明见[模型与工具](/lessons/12-model)。
