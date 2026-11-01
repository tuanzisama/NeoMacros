English | [简体中文](README.zh-CN.md)

# docs

The [VitePress](https://vitepress.dev/) documentation site, in English and
Chinese (`en/`, `zh/`, each with `guide/`, `examples/` and `api/` sections).

```bash
cd docs/vitepress
npm install
npm run dev      # local preview
npm run build    # static output
```

## Layout

| Path | Content |
|---|---|
| `vitepress/index.md` | landing page |
| `vitepress/{en,zh}/guide/` | tutorials: getting started, first script, events, permissions, debugging |
| `vitepress/{en,zh}/examples/` | complete runnable demo scripts |
| `vitepress/{en,zh}/api/` | `index.md` is hand-written; module pages are generated |
| `vitepress/api-examples/{en,zh}/` | per-module example snippets (hand-written source) |
| `vitepress/public/` | served as-is (`neomacros.d.ts`) |

## Generated API pages

Module pages under `en/api/` and `zh/api/` (except `index.md`) are build
artifacts — never edit them directly. They are produced by
[`docs-builder`](../docs-builder/README.md) and synced by
`scripts/ci/sync-generated-docs.sh`, which also appends the matching
`api-examples/<locale>/<module>.md` snippet under an `## Examples` heading.

To change examples on a module page, edit its snippet file and re-run:

```bash
./gradlew -p docs-builder generateApiDocs
bash scripts/ci/sync-generated-docs.sh
```
