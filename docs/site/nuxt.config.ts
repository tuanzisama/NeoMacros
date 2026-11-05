export default defineNuxtConfig({
  extends: ['docus'],
  modules: ['@nuxtjs/i18n'],

  // Root-domain hosting (Vercel): no base path, so the build stack's
  // canonical/sitemap/og URLs are all correct natively.
  site: {
    name: 'NeoMacros',
    url: 'https://neomacros-docs.vercel.app',
  },

  i18n: {
    defaultLocale: 'en',
    baseUrl: 'https://neomacros-docs.vercel.app',
    locales: [
      { code: 'en', name: 'English' },
      // Docus matches UI message packs by lowercase {code}.json and only
      // ships zh-CN.json, so the URL prefix is /zh-cn/ (html lang stays
      // zh-CN).
      { code: 'zh-CN', name: '中文' },
    ],
  },

  robots: {
    robotsTxt: false,
  },

  nitro: {
    // Static hosts resolve /path only when path/index.html exists; the
    // Docus layer writes flat .html files by default.
    prerender: {
      autoSubfolderIndex: true,
      // The definitions download is a prerendered server route (a *.d.ts
      // cannot be shipped from public/, see server/routes/).
      routes: ['/neomacros.d.ts'],
    },
  },
})
