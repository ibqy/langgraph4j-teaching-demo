<script setup lang="ts">
import { ref } from 'vue'
import { withBase } from 'vitepress'
import { groups } from '../courses'

const selected = ref(1)
const stages = [
  { name: '执行', node: 'draft', next: 'review', state: '草稿已生成', detail: '节点返回局部更新；Reducer 将更新合并到状态。' },
  { name: '暂停', node: 'draft', next: 'review', state: '等待人工决定', detail: 'interruptBefore 在审核节点之前停下，检查点保留恢复位置。' },
  { name: '恢复', node: 'review', next: '__END__', state: '注入批准结果', detail: '沿用 threadId，以 GraphInput.resume() 恢复，而不是重新开始。' }
]
</script>

<template>
  <div class="course-home">
    <section class="graph-hero" aria-labelledby="hero-title">
      <div class="hero-copy">
        <p class="eyebrow"><span class="eyebrow-line" aria-hidden="true"></span> LANGGRAPH4J / JAVA WORKFLOWS</p>
        <h1 id="hero-title">让每一步，<br><span>都有来路。</span></h1>
        <p class="hero-subtitle">从状态流转，到可恢复的业务工作流。</p>
        <p class="hero-description">18 个原生 LangGraph4j 示例。看见分支如何选择、状态如何汇合，以及工作流如何停下来，等待人的决定。</p>
        <div class="hero-actions">
          <a class="action-link action-primary" :href="withBase('/guide/getting-started')">开始学习 <span aria-hidden="true">→</span></a>
          <a class="action-link action-secondary" href="https://github.com/ibqy/langgraph4j-teaching-demo" target="_blank" rel="noopener noreferrer">查看源码 <span aria-hidden="true">↗</span><span class="sr-only">（新窗口）</span></a>
        </div>
        <p class="hero-note">无需 Key 起步 · 原生图引擎 · 中文逐课讲解</p>
      </div>
      <figure class="graph-figure checkpoint-figure">
        <div class="graph-figure-heading"><span>暂停，不是结束。</span><span class="figure-index">EXECUTION / 01</span></div>
        <div class="checkpoint-steps" aria-label="切换执行原理示意">
          <button v-for="(stage, index) in stages" :key="stage.name" type="button" :aria-pressed="selected === index" @click="selected = index">
            <span class="step-number">0{{ index + 1 }}</span><strong>{{ stage.name }}</strong><span aria-hidden="true">{{ index === 2 ? '■' : '→' }}</span>
          </button>
        </div>
        <div class="checkpoint-data" aria-live="polite" aria-atomic="true">
          <p class="snapshot-label">STATE SNAPSHOT</p>
          <h2>{{ stages[selected].state }}</h2>
          <dl>
            <div><dt>threadId</dt><dd>lesson-001</dd></div>
            <div><dt>node</dt><dd>{{ stages[selected].node }}</dd></div>
            <div><dt>next</dt><dd>{{ stages[selected].next }}</dd></div>
          </dl>
          <p class="snapshot-explanation">{{ stages[selected].detail }}</p>
        </div>
        <figcaption><span class="graph-legend"><i aria-hidden="true"></i>点击阶段了解原理</span><span>教学示意 · 非在线执行</span></figcaption>
      </figure>
    </section>

    <div class="version-strip" aria-label="课程技术版本">
      <div><span class="version-label">原生编排引擎</span><strong>LangGraph4j <span>1.9.0</span></strong></div>
      <div><span class="version-label">语言环境</span><strong>Java <span>21</span></strong></div>
      <div><span class="version-label">应用框架</span><strong>Spring Boot <span>3.5.8</span></strong></div>
    </div>

    <section class="curriculum" aria-labelledby="curriculum">
      <div class="section-heading">
        <div><p class="eyebrow">READ. RUN. REASON.</p><h2 id="curriculum">一条完整的学习路径</h2><p class="section-intro">基础 6 课 · 高阶 9 课 · 实战 3 课。读原理，跑源码，再验证自己的理解。</p></div>
        <nav class="course-jump" aria-label="课程阶段"><a v-for="group in groups" :key="group.id" :href="`#${group.id}`">{{ group.name.slice(0, 2) }}</a></nav>
      </div>
      <section v-for="group in groups" :key="group.id" class="curriculum-group" :aria-labelledby="group.id">
        <div class="group-heading"><p class="group-index">{{ group.range }} <span>/ {{ group.label }}</span></p><h3 :id="group.id">{{ group.name }}</h3><p>{{ group.description }}</p></div>
        <div class="lesson-grid">
          <p v-for="[slug, title, description] in group.lessons" :key="slug">
            <a :href="withBase(`/lessons/${slug}`)"><span class="lesson-number">{{ slug.slice(0, 2) }}</span><span class="lesson-copy"><strong>{{ title }}</strong><span>{{ description }}</span></span><span class="lesson-arrow" aria-hidden="true">→</span></a>
          </p>
        </div>
      </section>
    </section>

    <section class="learning-notes" aria-labelledby="learning-boundaries">
      <div class="notes-intro"><p class="eyebrow">KNOW THE BOUNDARIES</p><h2 id="learning-boundaries">真实的机制，<br>诚实的边界。</h2><p>先理解能做什么，<br>再判断何时值得使用。</p><p><a :href="withBase('/guide/comparison')">与 Alibaba Graph 对照 →</a></p></div>
      <div class="notes-content">
        <div class="learning-note"><span class="note-number">01</span><div><h3>编排真实，模型默认离线</h3><p>每课调用原生 StateGraph。模型课用明确标识的确定性实现演示模型与工具协议；真实模型需要单独配置，离线输出不代表模型质量。</p></div></div>
        <div class="learning-note"><span class="note-number">02</span><div><h3>网站读文档，服务在本地</h3><p>GitHub Pages 不运行 Java，也不保存你的审批状态。按<a :href="withBase('/guide/getting-started')">快速开始</a>启动本地服务，再通过运行接口观察执行、历史与恢复。</p></div></div>
        <div class="learning-note"><span class="note-number">03</span><div><h3>内存检查点不等于生产持久化</h3><p>MemorySaver、状态级幂等和本地业务资料用于教学。跨进程恢复、身份认证、持久化与外部业务副作用，需要额外工程设计。</p></div></div>
      </div>
    </section>
    <nav class="guide-links" aria-label="辅助指南"><span>把问题带回代码。</span><a :href="withBase('/guide/concepts')">核心概念 →</a><a :href="withBase('/guide/runtime')">运行接口 →</a><a :href="withBase('/guide/troubleshooting')">常见问题 →</a></nav>
  </div>
</template>

<style scoped>
.checkpoint-steps { display: grid; grid-template-columns: repeat(3, 1fr); border-bottom: 1px solid var(--graph-line); }
.checkpoint-steps button { display: grid; grid-template-columns: 1fr auto; gap: 16px 8px; padding: 20px; text-align: left; border-right: 1px solid var(--graph-line); color: var(--graph-muted); cursor: pointer; }
.checkpoint-steps button:last-child { border-right: 0; }
.checkpoint-steps button[aria-pressed='true'] { background: var(--graph-text); color: var(--graph-paper); }
.checkpoint-steps button:hover:not([aria-pressed='true']) { background: var(--graph-paper); color: var(--graph-text); }
.step-number { grid-column: 1 / -1; font-family: var(--vp-font-family-mono); font-size: 12px; }
.checkpoint-steps strong { font-size: 20px; }
.checkpoint-data { padding: 28px; min-height: 305px; }
.snapshot-label { color: var(--graph-muted); font-size: 11px; letter-spacing: 2px; }
.checkpoint-data h2 { margin: 12px 0 22px; font-size: 26px; }
.checkpoint-data dl { margin: 0; font-family: var(--vp-font-family-mono); font-size: 13px; }
.checkpoint-data dl div { display: flex; justify-content: space-between; gap: 12px; border-top: 1px solid var(--graph-line); padding: 8px 0; }
.checkpoint-data dt { color: var(--graph-muted); }
.checkpoint-data dd { margin: 0; }
.course-home .snapshot-explanation { margin-top: 16px; color: var(--graph-muted); font-size: 14px; line-height: 1.8; }
@media (max-width: 539px) { .checkpoint-data { padding: 22px; } .checkpoint-steps button { padding: 16px; } .checkpoint-data h2 { font-size: 24px; } }
</style>
