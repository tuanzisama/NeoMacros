[English](README.md) | 简体中文

# doc-api

标记脚本公开 API 面的注解集——脚本可调用的一切，以此为准。

注解（`@ApiModule`、`@ApiHandle`、`@ApiEvent`、`@ApiSince`、`@ApiPermission`、`@ApiExperimental` 等）装饰脚本可见的门面接口与事件；同一元素上的 Javadoc 承载描述文本，中文翻译写在 `@zh` 标签里。各标签的用法见 `package-info.java` 的 Javadoc。

注解以 jar 形式发布（`ink.tuanzi.neomacros:doc-api`），由独立的 [NeoMacros-Doc](https://github.com/tuanzisama/NeoMacros-Doc) 仓库中的文档生成器按坐标消费，生成双语文档与 `neomacros.d.ts` 类型定义。
