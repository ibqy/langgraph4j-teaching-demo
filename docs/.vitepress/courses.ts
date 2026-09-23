export const groups = [
  {
    id: 'basics', range: '01—06', name: '基础 · 建立图的直觉', label: 'FOUNDATION',
    description: '用真实节点、状态与边，让每一次执行都有迹可循。',
    lessons: [
      ['01-hello', 'Hello Graph', '从 START 到 END，运行第一张原生图'],
      ['02-sequence', '顺序编排', '让前一步的输出成为下一步的输入'],
      ['03-state', '状态与 Reducer', '区分替换、累加、去重与保留重复'],
      ['04-routing', '条件路由', '用业务数据选择真实执行分支'],
      ['05-loop', '循环与退出', '控制迭代次数，不让工作流失控'],
      ['06-parallel', '并行与汇合', '显式配置执行器，验证真正的并发']
    ]
  },
  {
    id: 'advanced', range: '07—15', name: '高阶 · 掌控执行过程', label: 'ENGINEERING',
    description: '理解暂停、恢复与副作用；把模型放进图，而不是把图藏进模型。',
    lessons: [
      ['07-subgraph', '子图组合', '让一段独立流程成为父图节点'],
      ['08-checkpoint', '检查点与历史', '保留状态快照，读取历史执行现场'],
      ['09-isolation', '线程隔离', '同一张图，不同 threadId 各自运行'],
      ['10-human-review', '人工介入', '暂停执行，注入决定，再恢复'],
      ['11-streaming', '节点流与 SSE', '观察真实节点事件，不伪装 Token 流'],
      ['12-model', '模型与工具', 'LangChain4j 模型协议与原生工具执行'],
      ['13-retry', '有界重试', '限定可重试失败与尝试次数'],
      ['14-idempotency', '幂等与副作用', '识别重复操作，理解状态级防重边界'],
      ['15-observability', '可观测性', '通过耗时与路径解释执行过程']
    ]
  },
  {
    id: 'practice', range: '16—18', name: '实战 · 完成业务闭环', label: 'IN PRACTICE',
    description: '三个可复现业务场景，包含分支、拒绝与恢复；业务数据明确使用本地样本。',
    lessons: [
      ['16-ticket', '工单处理', '分类、知识选择、起草与人工审核'],
      ['17-expense', '多级费用审批', '金额分流、经理与财务逐级审核'],
      ['18-research', '研究报告', '并行材料整理、综合结论与来源引用']
    ]
  }
]
