# docs-builder

独立 Gradle 构建（不属于主构建）：脚本 API 文档生成管线骨架。从 **doc-api 注解 + Javadoc（含 `@zh` 双语标签）** 生成四类产物：

- `api/<module>.md` — 英文页（规范源）；
- `api/<module>.zh.md` — 中文页（`@zh` 标签翻译，成员级缺失时回退英文）；
- `api/warnings.txt` — 生成告警清单（缺 `@zh` 翻译、缺 `@ApiSince`）；
- `types/neomacros.d.ts` — `declare module '@neomacros/*'` + `declare const neo`。

架构依据：[07-文档生成与测试](../../neomacros-docs/07-docs-generation-and-testing.md)、[05 §4 注解集](../../neomacros-docs/05-script-api.md)。

## 关键约束

- **单一真源**：以坐标依赖 `doc-api` 注解 jar（`ink.tuanzi.neomacros:doc-api`，来自 mavenLocal），**禁止**在本模块内定义镜像注解类（CI 字节码比对兜底，08 §1 规则 8）。
- Markdown 与 `.d.ts` 消费同一份 `ApiModel`（注解扫描产物），渲染层偏差由快照回归测试守门。
- 生成物不可手改；快照基线（`src/test/snapshots/`）仅在人工审查 diff 后更新。

## 使用

```bash
# 前置：主构建发布注解 jar 到本地仓库
./gradlew :doc-api:publishToMavenLocal

# 生成 + 快照回归 + TS 编译检查
./gradlew -p docs-builder check -PtscPath=<typescript bin/tsc 路径>

# 只生成（不经快照断言）：
# 产物落在 docs-builder/build/manual-gen/{api,types}/
./gradlew -p docs-builder test
```

`-PtscPath` 指向 typescript 包的 `bin/tsc` JS 文件（经 node 执行）；未提供时 TS 编译测试跳过（其余测试不受影响）。

## 结构

```
src/main/java/ink/tuanzi/neomacros/docs/
├── ApiModel.java        # 渲染层共用模型（模块/成员/句柄/事件/引用类型/告警）
├── ModelExtractor.java  # DocletEnvironment → ApiModel（含告警收集，顺序确定性排序）
├── DocletSupport.java   # Javadoc 摊平、@zh 标签提取、注解读取、Java→TS 类型映射
├── MDDoclet.java        # 双语 Markdown 渲染
└── TSDoclet.java        # TypeScript ambient 定义渲染
src/test/
├── fixtures/ink/tuanzi/neomacros/docs/fixture/   # fixture 门面（javadoc 工具的输入源码）
├── snapshots/api|types/                          # 产物快照基线（diff 入库）
└── java/ink/tuanzi/neomacros/docs/
    ├── GenerationHarness.java    # 进程内 javadoc 工具驱动（doc-api jar 走坐标解析）
    ├── DocsGenerationTest.java   # 快照比对 + 告警断言 + 回退/隐藏行为断言
    └── TsTypeCompileTest.java    # tsc --noEmit 类型编译检查（tscPath 未配置时跳过）
```

## 骨架期约定（后续任务可修订，修订需先回写 09 计划书）

- 事件 payload 接口发射在 `declare module '@neomacros/events'` 占位命名空间下（真实 events 模块落地后收敛）；
- 模块接口名 = 模块名 PascalCase + `Api` 后缀（对齐 05 §3 的 `EntityApi` 风格）；
- 中文页沿用英文页结构标题，仅正文按 `@zh` 翻译、缺失回退英文。
