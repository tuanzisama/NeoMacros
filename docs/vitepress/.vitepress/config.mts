import { defineConfig } from 'vitepress'

type Locale = 'en' | 'zh'

// API pages are generated from the facade sources by docs-builder; the lists
// below must cover the @ApiModule / @ApiClass / @ApiEvent values in
// core/.../api/ and core/.../event/def. They only order the sidebar entries.
const apiModules: string[] = [
  'events', 'clock', 'storage',
  'chat', 'hud', 'command', 'render',
  'world', 'player', 'entity', 'inventory',
  'fs', 'net', 'java', 'input',
]

// Classes live on their own generated pages under api/classes/.
const apiClasses: string[] = [
  'Vec3',
  'BlockPos',
  'Block',
  'BlockState',
  'RayHit',
  'JavaClass',
  'JavaObject',
  'Player',
  'LocalPlayer',
  'Entity',
  'Container',
  'Pos3d',
]

// Subscribable events (the ProductionEventSet roster) grouped by domain;
// each event has its own generated page under api/events/.
const eventDomains: Array<[string, string[]]> = [
  ['tick', ['tick']],
  ['chat', ['chat:send', 'chat:receive']],
  ['input', ['input:wheel']],
  ['key', ['key:press']],
  ['screen', ['screen:open', 'screen:close']],
  ['game', ['game:start', 'game:quit']],
  ['world', ['world:join', 'world:leave', 'world:dimensionChange', 'world:time', 'world:weather', 'world:blockUpdate', 'world:chunkLoad', 'world:chunkUnload', 'world:sound', 'world:bossbar']],
  ['player', ['player:join', 'player:leave', 'player:spawn', 'player:death', 'player:respawn', 'player:health', 'player:hunger', 'player:air', 'player:damage', 'player:heal', 'player:effect', 'player:exp', 'player:heldItem', 'player:gamemode', 'player:dimensionChange', 'player:attackBlock', 'player:attackEntity', 'player:interactBlock', 'player:interactEntity']],
  ['entity', ['entity:load', 'entity:unload', 'entity:damage', 'entity:heal']],
]

/** File-name slug of one event page under api/events/. */
function eventSlug(name: string) {
  return name.toLowerCase().replace(/:/g, '-')
}

const guidePages: Array<[string, Record<Locale, string>]> = [
  ['getting-started', { en: 'Getting Started', zh: '快速上手' }],
  ['first-script', { en: 'Your First Script', zh: '第一个脚本' }],
  ['events-and-async', { en: 'Events & Async', zh: '事件与异步' }],
  ['permissions-and-trust', { en: 'Permissions & Trust', zh: '权限与信任' }],
  ['commands-and-debugging', { en: 'Commands & Debugging', zh: '命令与排错' }],
]

const examplePages: Array<[string, Record<Locale, string>]> = [
  ['index', { en: 'Overview', zh: '总览' }],
  ['auto-greet', { en: 'Auto Greet', zh: '进服欢迎' }],
  ['coords-hud', { en: 'Coords HUD', zh: '坐标 HUD' }],
  ['block-guard', { en: 'Block Guard', zh: '方块守卫' }],
  ['damage-counter', { en: 'Damage Counter', zh: '伤害计数器' }],
]

function link(section: 'guide' | 'examples', page: string, locale: Locale) {
  return `/${locale}/${section}/${page}`
}

/** Sidebar shown on guide/examples/index pages (includes an API link). */
function defaultSidebar(locale: Locale) {
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
  ]
}

/** Sidebar shown ONLY on API pages (no guide/examples pollution). */
function apiSidebar(locale: Locale) {
  const t = locale === 'zh'
  return [
    {
      text: t ? 'API' : 'APIs',
      collapsed: false,
      items: [
        { text: t ? '总览' : 'Overview', link: `/${locale}/api/` },
        ...apiModules.map((name) => ({ text: name, link: `/${locale}/api/${name}` })),
      ],
    },
    {
      text: t ? '类' : 'Classes',
      collapsed: false,
      items: apiClasses.map((name) => ({ text: name, link: `/${locale}/api/classes/${name}` })),
    },
    {
      text: t ? '事件' : 'Events',
      collapsed: false,
      items: eventDomains.map(([domain, names]) => ({
        text: domain,
        collapsed: true,
        items: names.map((name) => ({
          text: name,
          link: `/${locale}/api/events/${eventSlug(name)}`,
        })),
      })),
    },
  ]
}

/** Path-keyed sidebars: API pages get a clean API-only sidebar. */
function allSidebars(locale: Locale) {
  const prefix = `/${locale}`
  const guide = defaultSidebar(locale)
  const api = apiSidebar(locale)
  return {
    [`${prefix}/guide/`]: guide,
    [`${prefix}/examples/`]: guide,
    [`${prefix}/api/`]: api,
    [prefix]: guide,
  }
}

/** Top nav shown on guide/examples pages only (hidden on API pages via CSS). */
function guideNav(locale: Locale) {
  const t = locale === 'zh'
  return [
    { text: t ? '指南' : 'Guide', link: `/${locale}/guide/getting-started` },
    { text: t ? '示例' : 'Examples', link: `/${locale}/examples/` },
  ]
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
        nav: guideNav('en'),
        sidebar: allSidebars('en'),
      },
    },
    zh: {
      label: '中文',
      lang: 'zh-CN',
      link: '/zh/',
      themeConfig: {
        nav: guideNav('zh'),
        sidebar: allSidebars('zh'),
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
    if (/^(en|zh)\/api\/.+\.md$/.test(path) && !path.endsWith('index.md')) {
      pageData.frontmatter ??= {}
      pageData.frontmatter.pageClass = 'api-facade'
      pageData.frontmatter.outline = [2, 3]
    }
    // API index page gets its own page class for CSS targeting.
    if (/^(en|zh)\/api\/index\.md$/.test(path)) {
      pageData.frontmatter ??= {}
      pageData.frontmatter.pageClass = 'api-index'
    }
  },
})
