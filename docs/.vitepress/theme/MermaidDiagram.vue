<script setup lang="ts">
import { getCurrentInstance, onMounted, ref, watch } from 'vue'
import { useData } from 'vitepress'

const props = defineProps<{ source: string }>()
const { isDark } = useData()
const svg = ref('')
const error = ref(false)
const id = `course-diagram-${getCurrentInstance()!.uid}`

onMounted(() => {
  watch([() => props.source, isDark], async (_, __, onCleanup) => {
    let active = true
    onCleanup(() => { active = false })
    error.value = false
    try {
      const { default: mermaid } = await import('mermaid')
      if (!active) return
      mermaid.initialize({
        startOnLoad: false,
        securityLevel: 'strict',
        theme: isDark.value ? 'dark' : 'neutral',
        fontFamily: 'Arial, Microsoft YaHei, sans-serif',
        flowchart: { htmlLabels: false, curve: 'linear' }
      })
      const result = await mermaid.render(id, props.source)
      if (active) svg.value = result.svg
    } catch {
      if (active) error.value = true
    }
  }, { immediate: true })
})
</script>

<template>
  <figure class="mermaid-diagram" aria-label="课程流程图">
    <p v-if="error" role="status">流程图暂时无法渲染，请展开下方源码阅读节点与连线。</p>
    <div v-else-if="svg" class="diagram-canvas" role="img" aria-label="课程节点与执行分支" v-html="svg"></div>
    <p v-else role="status">正在绘制流程图…</p>
  </figure>
</template>

<style scoped>
.mermaid-diagram { margin: 24px 0 12px; padding: 20px; border: 1px solid var(--graph-line); background: var(--graph-surface); overflow-x: auto; }
.mermaid-diagram p { color: var(--graph-muted); font-size: 14px; }
.diagram-canvas { text-align: center; }
.diagram-canvas :deep(svg) { display: block; margin: 0 auto; min-width: 260px; height: auto; }
</style>
