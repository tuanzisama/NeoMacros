English | [简体中文](README.zh-CN.md)

# doc-api

Annotations marking the public script API surface — the single source of truth for everything scripts can call.

The annotations (`@ApiModule`, `@ApiHandle`, `@ApiEvent`, `@ApiSince`, `@ApiPermission`, `@ApiExperimental`, ...) decorate the facade interfaces and events scripts see; Javadoc on the same elements carries the description, with `@zh` tags holding the Chinese translation. Javadoc on `package-info.java` explains each tag.

The annotation jar is published (`ink.tuanzi.neomacros:doc-api`) and consumed by [docs-builder](../docs-builder/), which turns it into the bilingual API docs and the `neomacros.d.ts` type definitions.
