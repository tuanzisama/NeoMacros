[English](README.md) | 简体中文

# docs

基于 [VitePress](https://vitepress.dev/) 的文档站，中英双语（`en/`、
`zh/`，每种语言下各有 `guide/`、`examples/`、`api/` 三个分区）。

```bash
cd docs/vitepress
npm install
npm run dev      # 本地预览
npm run build    # 静态产物
```

## 目录结构

| 路径 | 内容 |
|---|---|
| `vitepress/index.md` | 落地页 |
| `vitepress/{en,zh}/guide/` | 教程：快速上手、第一个脚本、事件、权限、排错 |
| `vitepress/{en,zh}/examples/` | 完整可运行的演示脚本 |
| `vitepress/{en,zh}/api/` | `index.md` 为手写；模块页为生成产物 |
| `vitepress/api-examples/{en,zh}/` | 各模块示例片段（手写源） |
| `vitepress/public/` | 原样伺服（`neomacros.d.ts`） |

## 生成的 API 页面

`en/api/`、`zh/api/` 下的模块页（`index.md` 除外）是构建产物——不要直
接编辑。它们由 [`docs-builder`](../docs-builder/README.zh-CN.md) 生成，
再经 `scripts/ci/sync-generated-docs.sh` 同步；同步时会把对应的
`api-examples/<locale>/<module>.md` 片段以 `## Examples` 标题追加到页尾。

想改模块页上的示例：编辑对应片段文件，然后重新执行：

```bash
./gradlew -p docs-builder generateApiDocs
bash scripts/ci/sync-generated-docs.sh
```
