import DefaultTheme from 'vitepress/theme'
import type { Theme } from 'vitepress'
import CourseHome from './CourseHome.vue'
import MermaidDiagram from './MermaidDiagram.vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('CourseHome', CourseHome)
    app.component('MermaidDiagram', MermaidDiagram)
  }
} satisfies Theme
