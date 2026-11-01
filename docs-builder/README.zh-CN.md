[English](README.md) | 简体中文

# docs-builder

独立 Gradle 构建（不属于主构建）。从 [doc-api](../doc-api/) 注解及其 Javadoc（含 `@zh` 双语标签）生成脚本 API 文档：

- `api/<module>.md` — 英文页（规范源）
- `api/<module>.zh.md` — 中文页，按 `@zh` 标签翻译，成员级缺失时回退英文
- `api/warnings.txt` — 生成告警清单（缺 `@zh`、缺 `@ApiSince`）
- `types/neomacros.d.ts` — `declare module '@neomacros/*'` 定义与 `neo` 全局对象

Markdown 与 `.d.ts` 消费同一份 `ApiModel`（注解扫描产物）。生成物不可手改；`src/test/snapshots/` 下的快照基线仅在人工审查 diff 后更新。

## 使用

```bash
# 前置：发布注解 jar 到本地仓库
./gradlew :doc-api:publishToMavenLocal

# 生成 + 快照回归 + TypeScript 编译检查
./gradlew -p docs-builder check -PtscPath=<typescript 包 bin/tsc 路径>

# 只生成（不做快照断言）；产物在 build/manual-gen/
./gradlew -p docs-builder test
```

`-PtscPath` 指向 typescript 包的 `tsc` JS 文件（经 node 执行）；未提供时跳过编译检查，其余测试不受影响。

## 结构

```
src/main/java/ink/tuanzi/neomacros/docs/
├── ApiModel.java        # 渲染层共用模型（模块/成员/句柄/事件/告警）
├── ModelExtractor.java  # DocletEnvironment → ApiModel，顺序确定性排序
├── DocletSupport.java   # Javadoc 摊平、@zh 标签提取、Java→TS 类型映射
├── MDDoclet.java        # 双语 Markdown 渲染
└── TSDoclet.java        # TypeScript ambient 定义渲染
```
