import { defineConfig } from 'vitepress'

type Locale = 'en' | 'zh'

// API module pages are generated from the facade sources by docs-builder;
// the grouping below must cover the @ApiModule values in core/.../api/.
const apiGroups: Array<{ label: Record<Locale, string>; modules: string[] }> = [
  { label: { en: 'Core', zh: '核心' }, modules: ['events', 'clock', 'storage'] },
  { label: { en: 'UI', zh: '界面' }, modules: ['chat', 'hud', 'command', 'render'] },
  { label: { en: 'World', zh: '世界' }, modules: ['world', 'player', 'entity'] },
  { label: { en: 'System', zh: '系统' }, modules: ['fs', 'net', 'java'] },
  { label: { en: 'Input', zh: '输入' }, modules: ['input'] },
]

const guidePages: Array<[string, Record<Locale, string]>] = [
  ['getting-started', { en: 'Getting Started', zh: '快速上手' }],
  ['first-script', { en: 'Your First Script', zh: '第一个脚本' }],
  ['events-and-async', { en: 'Events & Async', zh: '事件与异步' }],
  ['permissions-and-trust', { en: 'Permissions & Trust', zh: '权限与信任' }],
  ['commands-and-debugging', { en: 'Commands & Debugging', zh: '命令与排错' }],
]

const examplePages: Array<[string, Record<Locale, string]>] = [
  ['index', { en: 'Overview', zh: '总览' }],
  ['auto-greet', { en: 'Auto Greet', zh: '进服欢迎' }],
  ['coords-hud', { en: 'Coords HUD', zh: '坐标 HUD' }],
  ['block-guard', { en: 'Block Guard', zh: '方块守卫' }],
  ['damage-counter', { en: 'Damage Counter', zh: '伤害计数器' }],
]

function link(section: 'guide' | 'examples', page: string, locale: Locale) {
  return `/${locale}/${section}/${page}`
}

function sidebar(locale: Locale) {
  const t = locale === 'zh'
  return [
    {
      text: t ? '指南' : 'Guide',
      items: guidePages.map(([page, label]) => ({ text: label[locale], link: link('guide', page, locale) })),
    },
    {
      text: t ? '示例' : 'Examples',
      items: examplePages.map(([page, label]) => ({ text: label[locale], link: link('examples', page, locale) })),
    },
    {
      text: t ? 'API 参考' : 'API Reference',
      link: `/${locale}/api/`,
    },
    ...apiGroups.map((group) => ({
      text: group.label[locale],
      items: group.modules.map((name) => ({ text: name, link: `/${locale}/api/${name}` })),
    })),
  ]
}

/** Dropdown nav entry for the API Reference (all module pages + types). */
function apiNav(locale: Locale) {
  const t = locale === 'zh'
  const items: Array<{ text: string; link: string }> = [
    { text: t ? '总览' : 'Overview', link: `/${locale}/api/` },
    { text: t ? '类型定义' : 'Type Definitions', link: `/${locale}/api/types` },
  ]
  for (const group of apiGroups) {
    for (const name of group.modules) {
      items.push({ text: name, link: `/${locale}/api/${name}` })
    }
  }
  return {
    text: t ? 'API 参考' : 'API Reference',
    items,
  }
}

export default defineConfig({
  // Project Pages serves the site under /NeoMacros/; every asset URL is
  // prefixed with this. Must stay in sync with the repository name.
  base: '/NeoMacros/',
  title: 'NeoMacros',
  description: 'ECMAScript client scripting for Minecraft (Fabric)',
  locales: {
    en: {
      label: 'English',
      lang: 'en',
      link: '/en/',
      themeConfig: {
        nav: [
          { text: 'Guide', link: '/en/guide/getting-started' },
          { text: 'Examples', link: '/en/examples/' },
          apiNav('en'),
        ],
        sidebar: sidebar('en'),
      },
    },
    zh: {
      label: '中文',
      lang: 'zh-CN',
      link: '/zh/',
      themeConfig: {
        nav: [
          { text: '指南', link: '/zh/guide/getting-started' },
          { text: '示例', link: '/zh/examples/' },
          apiNav('zh'),
        ],
        sidebar: sidebar('zh'),
        search: {
          options: {
            translations: {
              button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
              modal: {
                noResultsText: '未找到结果',
                resetButtonTitle: '清除查询',
                footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' },
              },
            },
          },
        },
      },
    },
  },
  themeConfig: {
    socialLinks: [{ icon: 'github', link: 'https://github.com/tuanzisama/NeoMacros' }],
    search: { provider: 'local' },
    outline: { level: [2, 3], label: 'On this page' },
    footer: {
      message: 'Not affiliated with Mojang or Microsoft.',
      copyright: 'Copyright © 2026 NeoMacros contributors',
    },
  },
  transformPageData(pageData) {
    // Generated facade pages get a stable page class so the custom theme can
    // style their member headings as cards; the hand-written overview keeps
    // its own frontmatter. API pages also show h3 (method names) in the
    // right-side outline.
    const path = pageData.relativePath.replace(/\\/g, '/')
    if (/^(en|zh)\/api\/[^/]+\.md$/.test(path) && !path.endsWith('index.md')) {
      pageData.frontmatter ??= {}
      pageData.frontmatter.pageClass = 'api-facade'
      pageData.frontmatter.outline = [2, 3]
    }
  },
})
