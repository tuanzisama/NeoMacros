[English](README.md) | 简体中文

# docs

基于 [Docus](https://docus.dev/) 的文档站（Nuxt 层，内建 i18n、搜索与自动
侧栏），中英双语（`content/en/`、`content/zh-cn/`，每种语言下各有
`guide/`、`examples/`、`api/` 三个分区）。

```bash
cd docs/site
npm install
npm run dev       # 本地预览
npm run generate  # GitHub Pages 静态产物（.output/public）
```

## 目录结构

| 路径 | 内容 |
|---|---|
| `site/nuxt.config.ts`、`site/app.config.ts` | 引擎配置：base 路径、语言、分区导航、链接 |
| `site/app/` | 主题覆写：API 成员卡片样式、body class 钩子、页脚 |
| `site/content/{en,zh-cn}/index.md` | 落地页（每种语言一页） |
| `site/content/{en,zh-cn}/guide/` | 教程：快速上手、第一个脚本、事件、权限、排错 |
| `site/content/{en,zh-cn}/examples/` | 完整可运行的演示脚本 |
| `site/content/{en,zh-cn}/api/` | `index.md` 为手写；模块页为生成产物 |
| `site/api-examples/{en,zh}/` | 各模块示例片段（手写源） |
| `site/public/` | 原样伺服（`neomacros.d.ts`） |

## 生成的 API 页面

`content/en/api/`、`content/zh-cn/api/` 下的模块页（`index.md` 除外）是
构建产物——不要直接编辑。它们由
[`docs-builder`](../docs-builder/README.zh-CN.md) 生成，再经
`scripts/ci/sync-generated-docs.sh` 同步；同步时会把对应的
`api-examples/<locale>/<module>.md` 片段以 `## Examples` 标题追加到页尾，
并把事件页按域归入子目录（`api/events/<域>/`）、剥掉生成页相对链接中的
`.html` 后缀、写入 API 子树的侧栏标题。

想改模块页上的示例：编辑对应片段文件，然后重新执行：

```bash
./gradlew -p docs-builder generateApiDocs
bash scripts/ci/sync-generated-docs.sh
```
