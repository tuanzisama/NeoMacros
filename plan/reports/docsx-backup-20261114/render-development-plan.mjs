// Renders neomacros-docsx/15-jsmacros-gap-development-plan.md from gap/tasks.json.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const dumpRoot = resolve(here, '..', '..');
const repoRoot = resolve(dumpRoot, '..');
const data = JSON.parse(readFileSync(join(dumpRoot, 'gap', 'tasks.json'), 'utf8'));
const gap = JSON.parse(readFileSync(join(dumpRoot, 'gap', 'gap.json'), 'utf8'));

const lines = [];
const out = (s = '') => lines.push(s);
const c = (s) => `\`${String(s ?? '').replace(/`/g, '\\`')}\``;
const esc = (s) => String(s ?? '').replace(/\|/g, '\\|').replace(/\n/g, ' ');
const pct = (n, d) => (d ? `${((n / d) * 100).toFixed(1)}%` : '—');

const STATUS = {
  ready: '可认领', blocked: '阻塞', 'design-first': '设计先行', 'no-op': '不实施',
};
const PHASE = {
  W0: 'W0 基线与设计', W1: 'W1 快速收益', W2: 'W2 数据/容器/物种', W3: 'W3 渲染/GUI/包级', '—': '不实施',
};
const REPO = { neomacros: 'NeoMacros', 'jsmacros-compat': 'jsmacros-compat', docs: 'docs' };

function memberNames(t) {
  return [...new Set(t.members.map((m) => m.name))];
}

function memberLine(t) {
  const names = memberNames(t);
  if (!names.length) return t.status === 'no-op' ? '无（不实施）' : '无成员（设计/占位任务）';
  const shown = names.slice(0, 18);
  const suffix = names.length > shown.length ? ` … 共 ${names.length} 个名 / ${t.memberCount} 个签名，全量见 ${c(`gap/tasks.json#${t.id}`)}` : '';
  return shown.map(c).join(' ') + suffix;
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------
out('# 15 · JsMacros 缺口开发改造计划（子-agent 执行版）');
out();
out('> 定位：14 号回答“缺什么”，本册回答“怎么分批做、每批改哪里、如何验收”。');
out('> 基线：JsMacros-CE `' + data.meta.jsmacrosCommit.slice(0, 8) + '`（2026-10-26） × NeoMacros `' + data.meta.neomacrosCommit.slice(0, 8) + '`（2026-11-11 20:33）。');
out('> 数据源：[' + c('gap/gap.json') + '](../JsMacros-Dump/gap/gap.json)（617 类 / 5,730 个成员记录）、[' + c('gap/tasks.json') + '](../JsMacros-Dump/gap/tasks.json)（' + data.summary.tasks + ' 个工作包）。');
out('> 与 13 号的关系：13 号是缺口 API 的设计真源；本册只做执行拆分，命名/形态冲突以 12 号 + 13 号最新拍板为准。');
out('> 逐成员任务明细：[`tasks-detail.md`](../JsMacros-Dump/gap/tasks-detail.md)（按任务列出每个成员/签名/状态/理由）。');
out();

out('## 0. 一眼看懂');
out();
out('| 指标 | 数值 |');
out('| --- | ---: |');
out(`| 工作包 | ${data.summary.tasks} 个（含 3 个设计 spike + 1 个不实施占位） |`);
out(`| 活跃缺口成员（missing + partial） | ${data.summary.activeTotal} |`);
out(`| 已分配到 NeoMacros 开发任务 | ${data.summary.neomacrosAssigned} |`);
out(`| 已分配到 compat 任务 | ${data.summary.compatAssigned} |`);
out(`| 不映射 / 服务端-only（D） | ${data.summary.exclusions['EXCL-D']} |`);
out(`| 永久缺口（E） | ${data.summary.exclusions['EXCL-E']} |`);
out(`| 未分配（triage 兜底） | ${data.summary.unassigned} |`);
out(`| 另记：compat 过时缺口（已覆盖、待回桥） | ${data.summary.compatStale} |`);
out(`| 另记：CE 新增成员（旧 1.9.2 基线无） | ${data.summary.compatNew} |`);
out();
out('读法：');
out();
out('- **想快速提升兼容面**：先做 `G-004`（216 个兼容壳桩回桥，不写 NeoMacros 代码）与 `G-005`（CE 基线同步）。');
out('- **想做新能力**：按 `G-010` → `G-012` → `G-017/017B` → `G-019` 的 P0 顺序拿小包；每个包都有独立验收。');
out('- **渲染/GUI/包级**：先做 `G-001/002/003` 设计 spike，需求方确认后再开 `G-031`~`G-038`；不要直接写组件。');
out('- **不要做**：`G-040` 与 `EXCL-D`/`EXCL-E` 列出的成员；它们是既定 D/E，不是漏项。');
out();

out('## 1. 子-agent 使用协议');
out();
out('### 1.1 开工前必读');
out();
out('1. `neomacros-docsx/12-api-catalog.md`：全模块 API 设计真源（双层模型 / 命名铁律 / 事件目录）。');
out('2. `neomacros-docsx/13-jsmacros-gap-api.md`：缺口 API 的 A-E 归宿与命名；本册的 D/E 判定以它为准。');
out('3. `neomacros-docsx/14-jsmacros-dump-gap.md`：本册的数据来源与逐类缺口。');
out('4. `neomacros/plan/TASKS.md`：任务看板；开工前登记 Owner/分支/状态。');
out('5. `neomacros-docsx/09-development-plan.md` §0：Agent 使用说明 / DoD / 泳道 / E2E 规格。');
out();
out('### 1.2 认领规则');
out();
out('- 一个 sub-agent 一次只认领一个 `G-xxx`；在 `plan/TASKS.md` 增加一行，完成后标 `done` 并写 `plan/reports/G-xxx.md`。');
out();
out('看板行模板：');
out();
out('```text');
out('| G-xxx | <15 号任务标题> | <阶段> | in-progress | <agent> | g-xxx-<slug> | <日期> |');
out('```');
out('- 分支命名 `g-xxx-<slug>`；不要直接改 main。');
out('- 只改任务卡列出的文件/模块；需要跨任务改共享文件时，先在任务报告里写明并在看板协调，不要两个 agent 同时改同一文件。');
out('- `G-004`/`G-005`/`G-039` 只改 `jsmacros-compat`（该仓库无 git，禁止并发编辑）；其余任务只改 `neomacros`。');
out('- 发现成员应归 D/E 而不是 B/C 时，不要硬写；在报告里给出证据并回写 13 号/本册。');
out('- 任务成员含 `missing` 与 `partial`：`partial` 是能力缩减，先按 13 号确认能否用现有面回桥；能回桥的并入 `G-004`，不要重复造。');
out();
out('### 1.3 命名与形态硬规则');
out();
out('- 命名：Mojang 官方命名优先，其次 12 号 §4.0；无参状态属性化（`getX()` → `x`），动作保留动词（`grabMouse`），名词不作动词。');
out('- 类：实体物种子类一律 `XxxEntity` 后缀；容器特化 `XxxInventory`；不沿用 JsMacros 的 `XxxHelper` 命名。');
out('- 包装层签名零 raw MC 类型；穿透统一走 `.raw` / `neo.java`；缺席 = `undefined`，空 = `[]`，永不出 `null`。');
out('- 异步动作返回 `Promise`；同步只有 `onSync`；事件名 `domain:camelCase`。');
out('- 注解应标尽标：`@ApiSince` / `@ApiMC` / `@ApiLoader` / `@ApiReadOnly` / `@ApiOptional` / `@ApiPermission` / `@ApiType`；缺任一构建/文档门禁会失败。');
out('- 注释全英文、只描述功能；禁止任务号/过程性注释；禁止 facade/门面修辞；中文说明放 `@zh`。');
out();

out('## 2. 架构落点（每个任务都按这条竖切）');
out();
out('```text');
out('ports/<X>Port.java + ports/model/*        中立端口 / 数据 record（无 MC 类型）');
out('        ↓');
out('core/api/<Module>Api.java + types/*       @ApiModule / @ApiClass facade（脚本面）');
out('        ↓');
out('product/adapter/Fabric<X>Port.java        适配器（主线程 / 映射 / mixin）');
out('        ↓');
out('core/event/def/* + ProductionEventSet    事件（如涉及）');
out('        ↓');
out('core/src/test + e2e + script-tests       单测 / E2E / 脚本用例');
out('        ↓');
out('doc-api javadoc + NeoMacros-Docs          d.ts / 类页 / 事件页');
out('```');
out();
out('本册路径约定（均相对 `neomacros/`）：`core/api` = `core/src/main/java/ink/tuanzi/neomacros/core/api`；`ports` = `ports/src/main/java/ink/tuanzi/neomacros/port`；`product/adapter` = `product/src/main/java/ink/tuanzi/neomacros/adapter`；`core/event/def` = `core/src/main/java/ink/tuanzi/neomacros/core/event/def`。');
out();
out('- 端口只加中立方法/record；`StubAdapter` 给默认实现，e2e 才能编译。');
out('- facade 只做参数归一/权限/异步编排；不直接碰 MC 类。');
out('- 适配器负责 MappingResolver / 主线程 / 26.x 与 1.21.6 差异；版本差异用 Stonecutter `//?` 或 `buildSrc/NeomacrosNode.kt` 替换表。');
out('- mixin 遵循既有惯例：interface + `@Accessor` + `neomacros$` 前缀，调用方 cast；mixin 内禁非 private 静态方法。');
out('- 事件：`core/event/def/<domain>/<X>Event.java` record + `ProductionEventSet` + 事件目录 + `Mutable*` 白名单；可取消性/方向写清。');
out('- 文档：javadoc + `@zh`/`@zh-param`/`@zh-return`；生成物在 `NeoMacros-Docs`，d.ts 不得出现 `unknown` 悬空引用。');
out();

out('## 3. 任务总览');
out();
out('| ID | 阶段 | 优先级 | 状态 | 规模 | 成员 | 任务 | 依赖 | 仓库 |');
out('| --- | --- | --- | --- | --- | ---: | --- | --- | --- |');
for (const t of data.tasks) {
  out(`| ${c(t.id)} | ${PHASE[t.phase] ?? t.phase} | ${t.priority} | ${STATUS[t.status] ?? t.status} | ${t.size} | ${t.memberCount} | ${esc(t.title)} | ${t.depends.length ? t.depends.map(c).join(' ') : '—'} | ${REPO[t.repo] ?? t.repo} |`);
}
out();
out('> `G-001/002/003` 是设计 spike，没有成员；`G-040` 是不实施占位。其余任务都带 `gap/tasks.json` 里的成员全量。');
out();

out('## 4. 任务卡');
out();
for (const t of data.tasks) {
  out(`### ${t.id} · ${t.title}`);
  out();
  out(`- 阶段/优先级/规模/状态：${PHASE[t.phase] ?? t.phase} / ${t.priority} / ${t.size} / ${STATUS[t.status] ?? t.status}`);
  out(`- 仓库：${REPO[t.repo] ?? t.repo}`);
  out(`- 依赖：${t.depends.length ? t.depends.map(c).join(' ') : '无'}`);
  out(`- 范围类：${t.classes.length ? t.classes.map(c).join(' ') : '无（设计/占位）'}`);
  out(`- 成员：${memberLine(t)}`);
  out(`- 落点：${t.targets.length ? t.targets.map(c).join(' ') : '见 notes'}`);
  out(`- 实施要点：${t.notes}`);
  out(`- 验收：${t.acceptance}`);
  if (t.status === 'blocked') out('- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。');
  if (t.status === 'design-first') out('- 交付物：设计稿（签名/端口/版本差异/测试策略/风险），不是代码。');
  if (t.status === 'no-op') out('- 不实施：只保留记录；若出现新证据，先回 13 号重新拍板。');
  out();
}

out('## 5. 排除项（不要开发）');
out();
out('### 5.1 D：不映射 / 服务端-only / 既定反模式（' + data.summary.exclusions['EXCL-D'] + ' 成员）');
out();
const dGroups = new Map();
for (const m of data.exclusions['EXCL-D']) {
  if (!dGroups.has(m.class)) dGroups.set(m.class, []);
  dGroups.get(m.class).push(m);
}
out('| 类 | 成员数 | 代表成员 | 理由 |');
out('| --- | ---: | --- | --- |');
for (const [cls, members] of [...dGroups.entries()].sort((a, b) => b[1].length - a[1].length)) {
  const names = [...new Set(members.map((m) => m.name))];
  out(`| ${c(cls)} | ${members.length} | ${names.slice(0, 8).map(c).join(' ')}${names.length > 8 ? ' …' : ''} | ${esc(members[0].reason || members[0].evidence || '既定 D')} |`);
}
out();
out('> D 的完整判定见 13 号 §2.2/§2.3；本表只做执行层汇总。');
out();
out('### 5.2 E：永久缺口（' + data.summary.exclusions['EXCL-E'] + ' 成员）');
out();
const eGroups = new Map();
for (const m of data.exclusions['EXCL-E']) {
  if (!eGroups.has(m.class)) eGroups.set(m.class, []);
  eGroups.get(m.class).push(m);
}
out('| 类 | 成员数 | 代表成员 | 理由 |');
out('| --- | ---: | --- | --- |');
for (const [cls, members] of [...eGroups.entries()].sort((a, b) => b[1].length - a[1].length)) {
  const names = [...new Set(members.map((m) => m.name))];
  out(`| ${c(cls)} | ${members.length} | ${names.slice(0, 8).map(c).join(' ')}${names.length > 8 ? ' …' : ''} | 运行时 Java 编译 / 动态类生成 / Jar 加载 |`);
}
out();
out('### 5.3 内部实现 / 上游移除 / 工具排除');
out();
out('- 内部实现 252 类（GUI 61、mixin 64、access 25、config 11、client 内部 12、core 内部 45、wagyourgui/根工具等）：无脚本可见面，全部 `not-applicable`。');
out('- 上游已移除 21 类、工具/doclet 排除 15 类：不参与对比，见 14 号附录 / `_missing.json` / `_excluded.json`。');
out();

out('## 6. 并行与冲突矩阵');
out();
out('### 6.1 推荐并行波次');
out();
out('| 波次 | 可并行任务 | 说明 |');
out('| --- | --- | --- |');
out('| W0 | `G-004` ∥ `G-001` ∥ `G-002` ∥ `G-003` | compat 回桥与三条设计 spike 互不冲突 |');
out('| W1 | `G-012` ∥ `G-013` ∥ `G-017` ∥ `G-017B` ∥ `G-019` ∥ `G-010` ∥ `G-014` | 实体/事件/数据/容器各一条竖切 |');
out('| W2 | `G-020` ∥ `G-022` ∥ `G-024` ∥ `G-024B` ∥ `G-025` ∥ `G-026` ∥ `G-027` ∥ `G-028` ∥ `G-018` ∥ `G-018B` | 按模块/文件隔离；同文件任务串行 |');
out('| W3 | 设计确认后：`G-031` ∥ `G-032` 族 ∥ `G-033` 族 ∥ `G-035/036` 族 | 渲染/GUI/包级；先做基类再组件 |');
out();
out('### 6.2 共享文件（必须串行或拆分）');
out();
out('| 文件/模块 | 相关任务 | 规则 |');
out('| --- | --- | --- |');
out('| `core/api/player/PlayerApi.java` + `ports/PlayerPort.java` + `FabricPlayerPort.java` | G-019/020/021/028/035/037 | 同时只能一个 owner；容器任务按顺序合并 |');
out('| `core/api/client/ClientApi.java` + `ports/ClientPort.java` + `FabricClientPort.java` | G-018B/024/024B/024C/025 | Options/Registry 串行合并 |');
out('| `core/api/CommandApi.java` + `CommandArg.java` + `CommandSpec.java` + `FabricCommandPort.java` | G-026/026B | 命令任务串行 |');
out('| `core/api/input/InputApi.java` + `ports/InputPort.java` | G-028/028B | 输入任务串行 |');
out('| `core/api/types/BlockState.java` + `Block.java` + `Chunk.java` | G-017/029 | 数据类任务串行 |');
out('| `core/api/render/components/*` | G-032/032B/033/033B/034/034B/034C/035/036/036B | 基类先合并，再组件；禁止并行改同一组件包 |');
out('| `core/api/entity/EntityApi.java` + `SpeciesEntities.java` + `EntityPort.java` + `FabricEntityPort.java` | G-012/022/023 | 实体基类先合并，物种任务再各自加类 |');
out('| `core/api/world/WorldApi.java` + `WorldPort.java` + `FabricWorldPort.java` | G-010/039 | G-039 在 compat 侧，不改 WorldApi |');
out('| `core/api/HudApi.java` + `RenderApi.java` + `FabricHudRegistry.java` | G-001/030/032/032B/033/033B/034/034B/034C/035/036/036B/038 | 先 G-001 定模型，再按基类→组件顺序 |');
out('| `core/api/types/ItemStack.java` + `ItemStackBuilder.java` | G-017/027/027B | ItemStack 任务串行 |');
out('| `core/event/def/**` | G-013/014/015/016/031 | 按 domain 目录隔离，可并行；ProductionEventSet 单点修改需协调 |');
out('| `product/adapter/PortEventWiring.java` | G-014/015/016 | 单点文件，按任务顺序合并 |');
out('| `jsmacros-compat/src/**` | G-004/005/039 | 无 git；同一时间只允许一个 owner |');
out();

out('## 7. DoD 与验证命令');
out();
out('每个任务完成必须同时满足：');
out();
out('1. **竖切完整**：port + facade + adapter + StubAdapter（如涉及）；事件进 `ProductionEventSet`。');
out('2. **注解完整**：`@ApiSince` / `@ApiMC` / `@ApiLoader` / `@ApiReadOnly` / `@ApiOptional` / `@ApiPermission` / `@ApiType` 按成员语义标注。');
out('3. **测试**：core 单测 + e2e 用例；动作类至少一个脚本级用例；mixin/版本差异有对应节点。');
out('4. **文档**：javadoc + `@zh`；`NeoMacros-Docs` 生成 d.ts/类页/事件页；d.ts 无 `unknown` 悬空引用。');
out('5. **构建**：`./gradlew build`；`./gradlew :core:test :e2e:test`；`bash scripts/smoke-build.sh`；四节点（1.21.6/26.1.2/26.2/26.3）编译绿。');
out('6. **CI**：推送后查 check-runs 全绿（不要只看 run 页面标题）。');
out('7. **报告**：`plan/reports/G-xxx.md` 写清改动文件、成员映射、D/E 结论、测试命令与结果、遗留。');
out();
out('compat 任务（G-004/005/039）额外：`npm test` 20/20；`npm run audit` 完备性门禁绿；`docs/missing-apis.md` 重新生成。');
out();

out('## 8. 风险与对策');
out();
out('| 风险 | 对策 |');
out('| --- | --- |');
out('| 成员被误判为 B/C，实际是服务端-only | 先查 13 号 §2.3；拿不到客户端值就登记 D，不挖无意义 accessor |');
out('| 26.x 私有 getter/字段不可达 | 先做 accessor 可行性验证；不可达则 D；不要为了一个成员引入大范围 mixin |');
out('| 渲染/GUI/包级任务过大 | G-001/002/003 先定模型；组件任务按基类→子类分波，单任务不超过 ~250 成员 |');
out('| 共享文件冲突 | 按 §6.2 串行；任务卡只列本任务文件；合并前 rebase |');
out('| 基线漂移 | 任务报告记录 NeoMacros HEAD；若 HEAD 变化，先跑 `gen-development-plan.mjs` 重生成任务索引 |');
out('| compat 无 git、易并发冲突 | 同一时间只允许一个 compat owner；改前备份/对比；audit 后立即生成 missing-apis |');
out();

out('## 9. 追溯与重生成');
out();
out('- 任务索引：`JsMacros-Dump/gap/tasks.json`；每个任务的 `members[]` 是 gap.json 成员的子集，含 `fqn/name/signature/status/target/reason`。');
out('- 逐成员任务明细：`JsMacros-Dump/gap/tasks-detail.md`（人读版，按任务/排除项列出全部成员）。');
out('- 完整性校验：`node tools/gap/verify-plan.mjs`；当前 2,750 个活跃成员全部恰好分配一次（0 未分配、0 重复）。');
out('- 15 号的任务表/成员索引由 `tools/gap/gen-development-plan.mjs` 生成；要改任务范围/依赖/验收，改该脚本的 `TASKS` 表后重跑，不要手改本册任务卡。');
out('- 重新生成：');
out();
out('```bash');
out('cd C:/Projects/fabric-mod/JsMacros-Dump');
out('node tools/gap/gen-development-plan.mjs');
out('node tools/gap/render-development-plan.mjs');
out('```');
out();
out('- 如果发现 `G-TRIAGE` 不为 0，先在本册/13 号做 triage，不要直接开工。');
out('- 本册与 14 号、`tasks.json` 必须同批更新；任务号 `G-xxx` 一旦被认领不要复用。');
out();

writeFileSync(join(repoRoot, 'neomacros-docsx', '15-jsmacros-gap-development-plan.md'), lines.join('\n') + '\n', 'utf8');
console.log('written 15-jsmacros-gap-development-plan.md');
