export default defineNuxtConfig({
  extends: ['docus'],
  modules: ['@nuxtjs/i18n'],

  // GitHub Pages serves the site under /NeoMacros/; every route and asset
  // URL is prefixed with this. Must stay in sync with the repository name.
  app: {
    baseURL: '/NeoMacros/',
  },

  site: {
    name: 'NeoMacros',
    // Origin only: the sitemap and og:image URLs stack app.baseURL on top
    // of this, so a path here would double it. The canonical/og:url tags
    // that need the full path are rewritten after generation by
    // scripts/ci/fix-docs-base-urls.sh.
    url: 'https://tuanzisama.github.io',
  },

  i18n: {
    defaultLocale: 'en',
    // Full deployed URL: hreflang tags resolve from here. nuxt-site-config
    // warns about the path portion; that is expected for subpath hosting.
    baseUrl: 'https://tuanzisama.github.io/NeoMacros',
    locales: [
      { code: 'en', name: 'English' },
      // Docus matches UI message packs by lowercase {code}.json and only
      // ships zh-CN.json, so the URL prefix is /zh-cn/ (html lang stays
      // zh-CN).
      { code: 'zh-CN', name: '中文' },
    ],
  },

  // A generated robots.txt is incompatible with the /NeoMacros/ base URL.
  robots: {
    robotsTxt: false,
  },

  nitro: {
    // GitHub Pages resolves /path only when path/index.html exists; the
    // Docus layer writes flat .html files by default.
    prerender: {
      autoSubfolderIndex: true,
    },
  },
})
