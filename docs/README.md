English | [简体中文](README.zh-CN.md)

# docs

The [Docus](https://docus.dev/) documentation site (a Nuxt layer with
built-in i18n, search and auto-generated navigation), in English and
Chinese (`content/en/`, `content/zh-cn/`, each with `guide/`, `examples/`
and `api/` sections).

```bash
cd docs/site
npm install
npm run dev       # local preview
npm run generate  # static output for GitHub Pages (.output/public)
```

## Layout

| Path | Content |
|---|---|
| `site/nuxt.config.ts`, `site/app.config.ts` | engine config: base URL, locales, section navigation, links |
| `site/app/` | theme overrides: API member card styles, body class hook, footer |
| `site/content/{en,zh-cn}/index.md` | landing pages (one per locale) |
| `site/content/{en,zh-cn}/guide/` | tutorials: getting started, first script, events, permissions, debugging |
| `site/content/{en,zh-cn}/examples/` | complete runnable demo scripts |
| `site/content/{en,zh-cn}/api/` | `index.md` is hand-written; module pages are generated |
| `site/api-examples/{en,zh}/` | per-module example snippets (hand-written source) |
| `site/public/` | served as-is (`neomacros.d.ts`) |

## Generated API pages

Module pages under `content/en/api/` and `content/zh-cn/api/` (except
`index.md`) are build artifacts — never edit them directly. They are
produced by [`docs-builder`](../docs-builder/README.md) and synced by
`scripts/ci/sync-generated-docs.sh`, which also appends the matching
`api-examples/<locale>/<module>.md` snippet under an `## Examples`
heading, groups event pages into per-domain subfolders
(`api/events/<domain>/`), strips the VitePress-style `.html` suffix from
relative class links, and writes the API subtree's sidebar titles.

To change examples on a module page, edit its snippet file and re-run:

```bash
./gradlew -p docs-builder generateApiDocs
bash scripts/ci/sync-generated-docs.sh
```
