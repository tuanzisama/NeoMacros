import { defineConfig } from 'vitepress'

export default defineConfig({
  locales: {
    en: { label: 'English', lang: 'en', link: '/en/' },
    zh: { label: '中文', lang: 'zh-CN', link: '/zh/' },
  },
  themeConfig: {
    nav: [
      { text: 'Guide', link: '/en/guide/getting-started' },
      { text: 'API', link: '/en/api/' },
    ],
  },
})
