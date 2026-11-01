import { defineConfig } from 'vitepress'

// API module pages are generated from the facade sources by docs-builder;
// this list must cover the @ApiModule values in core/src/main/java/.../api/.
const apiModules = [
  'chat', 'clock', 'command', 'entity', 'events', 'fs', 'hud',
  'input', 'net', 'player', 'render', 'storage', 'world',
]

export default defineConfig({
  // Project Pages serves the site under /NeoMacros/; every asset URL is
  // prefixed with this. Must stay in sync with the repository name.
  base: '/NeoMacros/',
  locales: {
    en: { label: 'English', lang: 'en', link: '/en/' },
    zh: { label: '中文', lang: 'zh-CN', link: '/zh/' },
  },
  themeConfig: {
    nav: [
      { text: 'Guide', link: '/en/guide/getting-started' },
      { text: 'API', link: '/en/api/' },
    ],
    sidebar: {
      '/en/api/': [
        {
          text: 'API Reference',
          link: '/en/api/',
          items: apiModules.map((m) => ({ text: m, link: `/en/api/${m}` })),
        },
      ],
      '/zh/api/': [
        {
          text: 'API 参考',
          link: '/zh/api/',
          items: apiModules.map((m) => ({ text: m, link: `/zh/api/${m}` })),
        },
      ],
    },
  },
})
