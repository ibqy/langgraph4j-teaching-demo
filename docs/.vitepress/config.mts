import { defineConfig } from 'vitepress'
import { groups } from './courses'

const repository = 'https://github.com/ibqy/langgraph4j-teaching-demo'
const guides = [
  ['getting-started', '开始学习'], ['concepts', '核心概念'], ['runtime', '运行接口'],
  ['comparison', '与 Alibaba Graph 对照'], ['troubleshooting', '常见问题'], ['boundaries', '能力与边界']
]

export default defineConfig({
  lang: 'zh-CN',
  title: 'LangGraph4j 学习实战',
  description: '18 课原生 LangGraph4j：从节点与状态，到检查点、人工介入、模型工具和业务闭环。Java 21，默认无需 API Key。',
  base: '/langgraph4j-teaching-demo/',
  cleanUrls: true,
  lastUpdated: true,
  head: [
    ['meta', { name: 'theme-color', content: '#e60012' }],
    ['link', { rel: 'icon', type: 'image/svg+xml', href: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"%3E%3Cpath fill="%23171717" d="M0 0h32v32H0z"/%3E%3Cpath fill="none" stroke="white" stroke-width="2" d="M8 8l16 8-16 8V8"/%3E%3Cpath fill="%23e60012" d="M5 5h6v6H5zM21 13h6v6h-6zM5 21h6v6H5z"/%3E%3C/svg%3E' }]
  ],
  markdown: {
    lineNumbers: true,
    theme: { light: 'github-light', dark: 'github-dark' },
    config(md) {
      // 把 ../ 或 ../../ 开头的仓库内源码链接重写为 GitHub 绝对地址
      const defaultLink =
        md.renderer.rules.link_open ||
        ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))
      md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
        const href = tokens[idx].attrGet('href')
        if (href && href.startsWith('../')) {
          const rel = href.replace(/^(\.\.\/)+/, '')
          const kind = /\.[A-Za-z]+$/.test(rel) ? 'blob' : 'tree'
          tokens[idx].attrSet('href', `${repository}/${kind}/main/${rel}`)
        }
        return defaultLink(tokens, idx, options, env, self)
      }

      const fence = md.renderer.rules.fence!
      md.renderer.rules.fence = (tokens, index, options, env, self) => {
        const token = tokens[index]
        const code = fence(tokens, index, options, env, self)
        if (token.info.trim() !== 'mermaid') return code
        const source = md.utils.escapeHtml(JSON.stringify(token.content))
        return `<MermaidDiagram :source="${source}" /><details><summary>查看流程图源码</summary>${code}</details>`
      }
    }
  },
  themeConfig: {
    siteTitle: 'LANGGRAPH4J / 学习实战',
    nav: [
      { text: '课程地图', link: '/#curriculum' },
      { text: '学习指南', link: '/guide/getting-started' },
      { text: '框架对照', link: '/guide/comparison' },
      { text: '源码', link: repository },
      { text: '学习路径', link: 'https://ibqy.github.io/curriculum' }
    ],
    sidebar: [
      { text: '学习指南', items: guides.map(([slug, text]) => ({ text, link: `/guide/${slug}` })) },
      ...groups.map(group => ({
        text: `${group.range} / ${group.name}`, collapsed: false,
        items: group.lessons.map(([slug, title]) => ({ text: `${slug.slice(0, 2)} · ${title}`, link: `/lessons/${slug}` }))
      }))
    ],
    outline: { level: [2, 3], label: '本页目录' },
    sidebarMenuLabel: '课程目录', returnToTopLabel: '返回顶部',
    darkModeSwitchLabel: '外观', lightModeSwitchTitle: '切换至浅色模式', darkModeSwitchTitle: '切换至深色模式',
    skipToContentLabel: '跳转到内容',
    docFooter: { prev: '上一课', next: '下一课' },
    editLink: { pattern: `${repository}/edit/main/docs/:path`, text: '查看本页源码' },
    search: {
      provider: 'local',
      options: {
        detailedView: true,
        miniSearch: {
          options: {
            tokenize: (text) => Array.from(new Intl.Segmenter('zh-CN', { granularity: 'word' }).segment(text))
              .filter(part => part.isWordLike).map(part => part.segment)
          },
          searchOptions: { combineWith: 'AND', fuzzy: false, prefix: true }
        },
        locales: { root: { translations: {
          button: { buttonText: '搜索文档', buttonAriaLabel: '搜索课程与指南' },
          modal: { noResultsText: '未找到相关内容', resetButtonTitle: '清空搜索', backButtonTitle: '关闭搜索',
            displayDetails: '显示详情', footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' } }
        } } }
      }
    },
    footer: { message: '原生引擎，离线教学。GitHub Pages 仅展示文档，Java 服务在本地运行。', copyright: 'MIT License · 2026 ibqy' }
  }
})
