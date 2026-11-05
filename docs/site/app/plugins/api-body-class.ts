// Generated API pages get a body class so app/app.css can present their
// member headings as cards; the hand-written API overview keeps its plain
// look. This replaces the old VitePress transformPageData pageClass hook.
export default defineNuxtPlugin(() => {
  const route = useRoute()
  const isApiFacade = computed(() => /^\/(en|zh-cn)\/api\/.+/.test(route.path))

  useHead({
    bodyAttrs: {
      class: () => (isApiFacade.value ? ['api-facade'] : []),
    },
  })
})
