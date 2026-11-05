[English](README.md) | 简体中文

# docs

基于 [Docus](https://docus.dev/) 的文档站（Nuxt 层，内建 i18n、搜索与自动
侧栏），中英双语（`content/en/`、`content/zh-cn/`，每种语言下各有
`guide/`、`examples/`、`api/` 三个分区）。

```bash
cd docs/site
npm install
npm run dev       # 本地预览
npm run generate  # 静态产物（.output/public）
```

## 部署

站点经 Vercel 的 Git 集成部署：导入仓库时把 **Root Directory 设为
`docs/site`**；该目录下的 `vercel.json` 固定了静态构建（`npm run
generate` → `.output/public`）并把 `/` 按访问者语言重定向。部署域名的
URL（`nuxt.config.ts` 里的 `site.url` 与 `i18n.baseUrl`）必须与 Vercel
项目域名一致。

API 文档自动再生：`docs` 工作流在 API 相关推送上自动重跑管线并提交刷新
后的内容树，Vercel 随之部署。请在项目 **Ignored Build Step**（设置 →
Git）里填入下面的命令，让纯 Java 推送不产生带旧文档的部署：

```
bash -c 'git diff --name-only HEAD^ HEAD -- docs/site | grep -q . && exit 1 || exit 0'
```

## 目录结构

| 路径 | 内容 |
|---|---|
| `site/nuxt.config.ts`、`site/app.config.ts` | 引擎配置：语言、站点 URL、分区导航、链接 |
| `site/app/` | 主题覆写：API 成员卡片样式、body class 钩子、页脚 |
| `site/vercel.json` | 静态构建命令 + `/` 按语言协商重定向 |
| `site/content/{en,zh-cn}/index.md` | 落地页（每种语言一页） |
| `site/content/{en,zh-cn}/guide/` | 教程：快速上手、第一个脚本、事件、权限、排错 |
| `site/content/{en,zh-cn}/examples/` | 完整可运行的演示脚本 |
| `site/content/{en,zh-cn}/api/` | `index.md` 为手写；模块页为生成产物、已入库 |
| `site/api-examples/{en,zh}/` | 各模块示例片段（手写源） |
| `site/server/` | 预渲染下载路由：`server/assets/neomacros.d.ts` → `/neomacros.d.ts` |

## 生成的 API 页面

`content/en/api/`、`content/zh-cn/api/` 下的模块页（`index.md` 除外）
是生成产物且**已入库**——因为 Vercel 从 git 构建站点、没有 Java 工具链。
它们由 [`docs-builder`](../docs-builder/README.zh-CN.md) 生成——不要直
接编辑。同步脚本还会把对应的 `api-examples/<locale>/<module>.md` 片段
以 `## Examples` 标题追加到页尾，把事件页按域归入子目录
（`api/events/<域>/`）、剥掉生成页相对链接中的 `.html` 后缀、写入 API
子树的侧栏标题。

改动门面源码或片段文件后，重新生成并提交：

```bash
./gradlew :doc-api:publishToMavenLocal
./gradlew -p docs-builder generateApiDocs
bash scripts/ci/sync-generated-docs.sh
```

CI 会跑同一管线，提交内容与其输出有漂移时构建失败。
