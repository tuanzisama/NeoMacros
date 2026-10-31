# docs-builder（占位）

独立 Gradle 构建（不属于主构建）：doclet（MDDoclet 双语 / TSDoclet）、构建约定插件、分发任务。

- 落地任务：T-024（开发计划书 09），架构依据：[07-文档生成与测试](../../neomacros-docs/07-docs-generation-and-testing.md)。
- 关键约束：以坐标依赖 `doc-api` 注解 jar（单一真源，禁止镜像注解类）。
