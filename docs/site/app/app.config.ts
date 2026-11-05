export default defineAppConfig({
  header: {
    title: 'NeoMacros',
  },
  seo: {
    description: 'ECMAScript client scripting for Minecraft (Fabric)',
  },
  // Top-level content folders become sections (Guide / Examples / API) and
  // the left sidebar shows only the active section's pages.
  navigation: {
    sub: 'header',
  },
  toc: {
    title: 'On this page',
  },
  socials: {
    github: 'https://github.com/tuanzisama/NeoMacros',
  },
  // Enables the header GitHub icon plus "Edit this page" links pointing at
  // the docs sources inside this repository.
  github: {
    url: 'https://github.com/tuanzisama/NeoMacros',
    branch: 'main',
    rootDir: 'docs/site',
  },
})
