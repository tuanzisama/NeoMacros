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
npm run generate  # static output (.output/public)
```

## Deployment

The site deploys to Vercel through the Git integration: import the
repository with **Root Directory = `docs/site`**; `vercel.json` in that
directory pins the static build (`npm run generate` → `.output/public`)
and redirects `/` to the visitor's language. The URLs of the deployed
site (in `nuxt.config.ts`: `site.url` and `i18n.baseUrl`) must match the
Vercel project domain.

## Layout

| Path | Content |
|---|---|
| `site/nuxt.config.ts`, `site/app.config.ts` | engine config: locales, site URL, section navigation, links |
| `site/app/` | theme overrides: API member card styles, body class hook, footer |
| `site/vercel.json` | static build commands + `/` language-negotiated redirect |
| `site/content/{en,zh-cn}/index.md` | landing pages (one per locale) |
| `site/content/{en,zh-cn}/guide/` | tutorials: getting started, first script, events, permissions, debugging |
| `site/content/{en,zh-cn}/examples/` | complete runnable demo scripts |
| `site/content/{en,zh-cn}/api/` | `index.md` is hand-written; module pages are generated and committed |
| `site/api-examples/{en,zh}/` | per-module example snippets (hand-written source) |
| `site/server/` | prerendered download route: `server/assets/neomacros.d.ts` → `/neomacros.d.ts` |

## Generated API pages

Module pages under `content/en/api/` and `content/zh-cn/api/` (except
`index.md`) are generated and **committed**, because Vercel builds the
site from git without a Java toolchain. They are produced by
[`docs-builder`](../docs-builder/README.md) — never edit them directly.
The sync script also appends the matching
`api-examples/<locale>/<module>.md` snippet under an `## Examples`
heading, groups event pages into per-domain subfolders
(`api/events/<domain>/`), strips the VitePress-style `.html` suffix from
relative class links, and writes the API subtree's sidebar titles.

After changing facade sources or a snippet file, regenerate and commit:

```bash
./gradlew :doc-api:publishToMavenLocal
./gradlew -p docs-builder generateApiDocs
bash scripts/ci/sync-generated-docs.sh
```

CI runs the same pipeline and fails when the committed trees have drifted
from its output.
