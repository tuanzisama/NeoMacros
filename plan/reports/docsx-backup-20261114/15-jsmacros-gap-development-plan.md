# 15 · JsMacros 缺口开发改造计划（子-agent 执行版）

> 定位：14 号回答“缺什么”，本册回答“怎么分批做、每批改哪里、如何验收”。
> 基线：JsMacros-CE `83089a69`（2026-10-26） × NeoMacros `5ffcaf43`（2026-11-11 20:33）。
> 数据源：[`gap/gap.json`](../JsMacros-Dump/gap/gap.json)（617 类 / 5,730 个成员记录）、[`gap/tasks.json`](../JsMacros-Dump/gap/tasks.json)（47 个工作包）。
> 与 13 号的关系：13 号是缺口 API 的设计真源；本册只做执行拆分，命名/形态冲突以 12 号 + 13 号最新拍板为准。
> 逐成员任务明细：[`tasks-detail.md`](../JsMacros-Dump/gap/tasks-detail.md)（按任务列出每个成员/签名/状态/理由）。

## 0. 一眼看懂

| 指标 | 数值 |
| --- | ---: |
| 工作包 | 47 个（含 3 个设计 spike + 1 个不实施占位） |
| 活跃缺口成员（missing + partial） | 2750 |
| 已分配到 NeoMacros 开发任务 | 2317 |
| 已分配到 compat 任务 | 63 |
| 不映射 / 服务端-only（D） | 241 |
| 永久缺口（E） | 129 |
| 未分配（triage 兜底） | 0 |
| 另记：compat 过时缺口（已覆盖、待回桥） | 216 |
| 另记：CE 新增成员（旧 1.9.2 基线无） | 39 |

读法：

- **想快速提升兼容面**：先做 `G-004`（216 个兼容壳桩回桥，不写 NeoMacros 代码）与 `G-005`（CE 基线同步）。
- **想做新能力**：按 `G-010` → `G-012` → `G-017/017B` → `G-019` 的 P0 顺序拿小包；每个包都有独立验收。
- **渲染/GUI/包级**：先做 `G-001/002/003` 设计 spike，需求方确认后再开 `G-031`~`G-038`；不要直接写组件。
- **不要做**：`G-040` 与 `EXCL-D`/`EXCL-E` 列出的成员；它们是既定 D/E，不是漏项。

## 1. 子-agent 使用协议

### 1.1 开工前必读

1. `neomacros-docsx/12-api-catalog.md`：全模块 API 设计真源（双层模型 / 命名铁律 / 事件目录）。
2. `neomacros-docsx/13-jsmacros-gap-api.md`：缺口 API 的 A-E 归宿与命名；本册的 D/E 判定以它为准。
3. `neomacros-docsx/14-jsmacros-dump-gap.md`：本册的数据来源与逐类缺口。
4. `neomacros/plan/TASKS.md`：任务看板；开工前登记 Owner/分支/状态。
5. `neomacros-docsx/09-development-plan.md` §0：Agent 使用说明 / DoD / 泳道 / E2E 规格。

### 1.2 认领规则

- 一个 sub-agent 一次只认领一个 `G-xxx`；在 `plan/TASKS.md` 增加一行，完成后标 `done` 并写 `plan/reports/G-xxx.md`。

看板行模板：

```text
| G-xxx | <15 号任务标题> | <阶段> | in-progress | <agent> | g-xxx-<slug> | <日期> |
```
- 分支命名 `g-xxx-<slug>`；不要直接改 main。
- 只改任务卡列出的文件/模块；需要跨任务改共享文件时，先在任务报告里写明并在看板协调，不要两个 agent 同时改同一文件。
- `G-004`/`G-005`/`G-039` 只改 `jsmacros-compat`（该仓库无 git，禁止并发编辑）；其余任务只改 `neomacros`。
- 发现成员应归 D/E 而不是 B/C 时，不要硬写；在报告里给出证据并回写 13 号/本册。
- 任务成员含 `missing` 与 `partial`：`partial` 是能力缩减，先按 13 号确认能否用现有面回桥；能回桥的并入 `G-004`，不要重复造。

### 1.3 命名与形态硬规则

- 命名：Mojang 官方命名优先，其次 12 号 §4.0；无参状态属性化（`getX()` → `x`），动作保留动词（`grabMouse`），名词不作动词。
- 类：实体物种子类一律 `XxxEntity` 后缀；容器特化 `XxxInventory`；不沿用 JsMacros 的 `XxxHelper` 命名。
- 包装层签名零 raw MC 类型；穿透统一走 `.raw` / `neo.java`；缺席 = `undefined`，空 = `[]`，永不出 `null`。
- 异步动作返回 `Promise`；同步只有 `onSync`；事件名 `domain:camelCase`。
- 注解应标尽标：`@ApiSince` / `@ApiMC` / `@ApiLoader` / `@ApiReadOnly` / `@ApiOptional` / `@ApiPermission` / `@ApiType`；缺任一构建/文档门禁会失败。
- 注释全英文、只描述功能；禁止任务号/过程性注释；禁止 facade/门面修辞；中文说明放 `@zh`。

## 2. 架构落点（每个任务都按这条竖切）

```text
ports/<X>Port.java + ports/model/*        中立端口 / 数据 record（无 MC 类型）
        ↓
core/api/<Module>Api.java + types/*       @ApiModule / @ApiClass facade（脚本面）
        ↓
product/adapter/Fabric<X>Port.java        适配器（主线程 / 映射 / mixin）
        ↓
core/event/def/* + ProductionEventSet    事件（如涉及）
        ↓
core/src/test + e2e + script-tests       单测 / E2E / 脚本用例
        ↓
doc-api javadoc + NeoMacros-Docs          d.ts / 类页 / 事件页
```

本册路径约定（均相对 `neomacros/`）：`core/api` = `core/src/main/java/ink/tuanzi/neomacros/core/api`；`ports` = `ports/src/main/java/ink/tuanzi/neomacros/port`；`product/adapter` = `product/src/main/java/ink/tuanzi/neomacros/adapter`；`core/event/def` = `core/src/main/java/ink/tuanzi/neomacros/core/event/def`。

- 端口只加中立方法/record；`StubAdapter` 给默认实现，e2e 才能编译。
- facade 只做参数归一/权限/异步编排；不直接碰 MC 类。
- 适配器负责 MappingResolver / 主线程 / 26.x 与 1.21.6 差异；版本差异用 Stonecutter `//?` 或 `buildSrc/NeomacrosNode.kt` 替换表。
- mixin 遵循既有惯例：interface + `@Accessor` + `neomacros$` 前缀，调用方 cast；mixin 内禁非 private 静态方法。
- 事件：`core/event/def/<domain>/<X>Event.java` record + `ProductionEventSet` + 事件目录 + `Mutable*` 白名单；可取消性/方向写清。
- 文档：javadoc + `@zh`/`@zh-param`/`@zh-return`；生成物在 `NeoMacros-Docs`，d.ts 不得出现 `unknown` 悬空引用。

## 3. 任务总览

| ID | 阶段 | 优先级 | 状态 | 规模 | 成员 | 任务 | 依赖 | 仓库 |
| --- | --- | --- | --- | --- | ---: | --- | --- | --- |
| `G-001` | W0 基线与设计 | P0 | 设计先行 | M | 0 | 设计 spike：hud.layer 与 Draw2D 元素模型 | — | NeoMacros |
| `G-002` | W0 基线与设计 | P0 | 设计先行 | M | 0 | 设计 spike：packet 管线与信任门 | — | NeoMacros |
| `G-003` | W0 基线与设计 | P0 | 设计先行 | M | 0 | 设计 spike：脚本 GUI 构建器 | — | NeoMacros |
| `G-004` | W0 基线与设计 | P0 | 可认领 | L | 230 | compat 回桥波：216 个“NeoMacros 已具备、兼容壳仍为桩” | — | jsmacros-compat |
| `G-005` | W0 基线与设计 | P1 | 可认领 | S | 39 | compat 基线同步：CE 新增 39 成员 + 审计基线 | `G-004` | jsmacros-compat |
| `G-010` | W1 快速收益 | P0 | 可认领 | S | 4 | FWorld 世界查询补全 | — | NeoMacros |
| `G-012` | W1 快速收益 | P0 | 可认领 | L | 52 | 实体基类状态面 | — | NeoMacros |
| `G-013` | W1 快速收益 | P0 | 可认领 | S | 8 | 背包事件 payload 字段 | — | NeoMacros |
| `G-014` | W1 快速收益 | P1 | 可认领 | M | 37 | 玩家/实体事件 payload 字段 | — | NeoMacros |
| `G-015` | W1 快速收益 | P1 | 可认领 | S | 28 | 聊天/世界/输入/屏幕事件字段 | — | NeoMacros |
| `G-016` | W1 快速收益 | P2 | 可认领 | S | 5 | 缺失事件类型：资源包加载 / 名称变更 | — | NeoMacros |
| `G-017` | W1 快速收益 | P1 | 可认领 | M | 51 | 数据类小缺口：方块/区块 | — | NeoMacros |
| `G-017B` | W1 快速收益 | P1 | 可认领 | S | 35 | 位置/文本/数学小批 | — | NeoMacros |
| `G-018` | W1 快速收益 | P1 | 可认领 | M | 45 | 数据句柄扩展：进度/统计 | — | NeoMacros |
| `G-018B` | W1 快速收益 | P1 | 可认领 | M | 57 | 数据句柄扩展：状态/计分/模组 | — | NeoMacros |
| `G-019` | W1 快速收益 | P0 | 可认领 | M | 29 | 容器通用面 | — | NeoMacros |
| `G-020` | W2 数据/容器/物种 | P1 | 可认领 | L | 39 | 容器菜单面 | `G-019` | NeoMacros |
| `G-021` | W2 数据/容器/物种 | P1 | 可认领 | L | 36 | 容器特化面：村民/马/织机/创造 | `G-019` | NeoMacros |
| `G-022` | W2 数据/容器/物种 | P1 | 可认领 | L | 31 | 物种姿态/展示面 | — | NeoMacros |
| `G-023` | W2 数据/容器/物种 | P2 | 可认领 | L | 65 | 物种小缺口批（客户端可观察） | `G-012` | NeoMacros |
| `G-024` | W2 数据/容器/物种 | P1 | 可认领 | L | 81 | Options 根面 + Video | — | NeoMacros |
| `G-024B` | W2 数据/容器/物种 | P1 | 可认领 | L | 89 | Options：Chat / Control / Accessibility | `G-024` | NeoMacros |
| `G-024C` | W2 数据/容器/物种 | P2 | 可认领 | M | 47 | Options：Music / Skin | `G-024` | NeoMacros |
| `G-025` | W2 数据/容器/物种 | P1 | 可认领 | L | 42 | RegistryHelper 全谱 | — | NeoMacros |
| `G-026` | W2 数据/容器/物种 | P2 | 可认领 | L | 48 | 命令构建面：CommandBuilder | — | NeoMacros |
| `G-026B` | W2 数据/容器/物种 | P2 | 可认领 | M | 21 | 命令构建面：Manager / Suggestions / Node / Source | `G-026` | NeoMacros |
| `G-027` | W2 数据/容器/物种 | P1 | 可认领 | L | 41 | 物品面：ItemStack / Item | — | NeoMacros |
| `G-027B` | W2 数据/容器/物种 | P1 | 可认领 | M | 36 | 物品面：附魔 / 食物 / 创造 | `G-027` | NeoMacros |
| `G-028` | W2 数据/容器/物种 | P1 | 可认领 | L | 49 | 交互/目标面 | `G-012` | NeoMacros |
| `G-028B` | W2 数据/容器/物种 | P1 | 可认领 | S | 12 | 输入动作面：PlayerInput / KeyTracker | `G-028` | NeoMacros |
| `G-029` | W2 数据/容器/物种 | P2 | 可认领 | L | 99 | 方块状态类型化查询 | — | NeoMacros |
| `G-030` | W2 数据/容器/物种 | P2 | 可认领 | M | 40 | CustomImage 绘制面 | `G-001` | NeoMacros |
| `G-031` | W3 渲染/GUI/包级 | P2 | 阻塞 | XL | 157 | 包级 API（packet） | `G-002` | NeoMacros |
| `G-032` | W3 渲染/GUI/包级 | P2 | 阻塞 | M | 97 | Draw2D 容器与图层 | `G-001` | NeoMacros |
| `G-032B` | W3 渲染/GUI/包级 | P2 | 阻塞 | M | 63 | Draw2DElement 基类与 Builder | `G-032` | NeoMacros |
| `G-033` | W3 渲染/GUI/包级 | P2 | 阻塞 | M | 117 | Draw3D 与基础 3D 组件 | `G-001` | NeoMacros |
| `G-033B` | W3 渲染/GUI/包级 | P2 | 阻塞 | M | 120 | 3D 面/追踪线组件 | `G-033` | NeoMacros |
| `G-034` | W3 渲染/GUI/包级 | P2 | 阻塞 | L | 233 | 2D 组件：Text / Rect / Line | `G-032` | NeoMacros |
| `G-034B` | W3 渲染/GUI/包级 | P2 | 阻塞 | M | 165 | 2D 组件：Image / Item | `G-032` | NeoMacros |
| `G-034C` | W3 渲染/GUI/包级 | P2 | 阻塞 | S | 28 | 2D 组件基类：Alignable / RenderElement | `G-032` | NeoMacros |
| `G-035` | W3 渲染/GUI/包级 | P2 | 阻塞 | L | 52 | 脚本 GUI 核心 | `G-003` | NeoMacros |
| `G-036` | W3 渲染/GUI/包级 | P2 | 阻塞 | L | 62 | 脚本 GUI 控件：基础四类 | `G-035` | NeoMacros |
| `G-036B` | W3 渲染/GUI/包级 | P2 | 阻塞 | M | 86 | 脚本 GUI 控件：次级控件 | `G-036` | NeoMacros |
| `G-037` | W3 渲染/GUI/包级 | P2 | 阻塞 | S | 4 | FPlayer P3：签名/全景 | `G-003` | NeoMacros |
| `G-038` | W3 渲染/GUI/包级 | P2 | 阻塞 | M | 6 | 纹理/注册纹理面 | `G-001` | NeoMacros |
| `G-039` | W2 数据/容器/物种 | P1 | 可认领 | L | 49 | 世界扫描 DSL 对齐（compat） | `G-004` | jsmacros-compat |
| `G-040` | 不实施 | — | 不实施 | — | 0 | 永久缺口（不实施） | — | NeoMacros |

> `G-001/002/003` 是设计 spike，没有成员；`G-040` 是不实施占位。其余任务都带 `gap/tasks.json` 里的成员全量。

## 4. 任务卡

### G-001 · 设计 spike：hud.layer 与 Draw2D 元素模型

- 阶段/优先级/规模/状态：W0 基线与设计 / P0 / M / 设计先行
- 仓库：NeoMacros
- 依赖：无
- 范围类：无（设计/占位）
- 成员：无成员（设计/占位任务）
- 落点：`core/api/HudApi.java` `core/api/render/RenderApi.java` `ports/RenderPort.java` `product/adapter/FabricRenderPort.java` `product/adapter/FabricHudRegistry.java`
- 实施要点：先定持久元素/图层/生命周期（add/remove/clear、z-index、parent/对齐、每帧重投），再拆组件类。T-516 的 hud.layer 模型未立项是本波的前置。
- 验收：设计稿回写 12 号（§4.15）与 15 号；列出 API 签名、端口契约、版本差异、测试策略；经需求方确认后才进入 G-032/033/034。
- 交付物：设计稿（签名/端口/版本差异/测试策略/风险），不是代码。

### G-002 · 设计 spike：packet 管线与信任门

- 阶段/优先级/规模/状态：W0 基线与设计 / P0 / M / 设计先行
- 仓库：NeoMacros
- 依赖：无
- 范围类：无（设计/占位）
- 成员：无成员（设计/占位任务）
- 落点：`ports/ClientPort.java` `product/adapter/FabricClientPort.java` `product/adapter/mixin/ClientPacketListenerMixin.java`
- 实施要点：T-517 后置中。需先定 packet:receive/send 事件、PacketBuffer 生命周期、高流量忽略名单、send 的信任门与权限键；再定 PacketByteBufferHelper 的成员映射。
- 验收：设计稿含事件方向/可取消性、包过滤、缓冲区所有权、失败语义、版本门（1.21.6/26.x）、真机验证清单；需求方确认 send 权限。
- 交付物：设计稿（签名/端口/版本差异/测试策略/风险），不是代码。

### G-003 · 设计 spike：脚本 GUI 构建器

- 阶段/优先级/规模/状态：W0 基线与设计 / P0 / M / 设计先行
- 仓库：NeoMacros
- 依赖：无
- 范围类：无（设计/占位）
- 成员：无成员（设计/占位任务）
- 落点：`core/api/player/PlayerApi.java` `product/fabric/ScriptScreen.java` `product/fabric/NeoMacrosFabric.java`
- 实施要点：T-521 P3。先定 Screen 句柄族与脚本构建器的边界、控件树/布局/事件回调、关闭与 Esc 语义、与 player.screen 只读面的关系。
- 验收：设计稿含 ScreenSpec/控件 API、生命周期、与 OpenGL/26.x extractRenderState 的接线、测试策略；确认后再做 G-035/036。
- 交付物：设计稿（签名/端口/版本差异/测试策略/风险），不是代码。

### G-004 · compat 回桥波：216 个“NeoMacros 已具备、兼容壳仍为桩”

- 阶段/优先级/规模/状态：W0 基线与设计 / P0 / L / 可认领
- 仓库：jsmacros-compat
- 依赖：无
- 范围类：`FKeyBind` `FPlayer` `FWorld` `FFS` `FReflection` `CustomImage` `CommandBuilder` `CreativeInventory` `EnchantInventory` `Inventory` `RecipeInventory` `StoneCutterInventory` `RegistryHelper` `Draw2D` `IScreen` `TextBuilder` `InteractionManagerHelper` `ItemStackHelper` `OptionsHelper` `PacketByteBufferHelper` `TextHelper` `BlockStateHelper` `ChunkHelper` `ClientPlayerEntityHelper` `EntityHelper` `ItemEntityHelper` `LivingEntityHelper` `MerchantEntityHelper` `MobEntityHelper` `PlayerEntityHelper` `EnderDragonEntityHelper` `WitherEntityHelper` `ArmorStandEntityHelper` `EndCrystalEntityHelper` `ItemFrameEntityHelper` `PaintingEntityHelper` `DisplayEntityHelper` `ItemDisplayEntityHelper` `CreeperEntityHelper` `EndermanEntityHelper` `GuardianEntityHelper` `PhantomEntityHelper` `PiglinEntityHelper` `ShulkerEntityHelper` `SlimeEntityHelper` `SpellcastingIllagerEntityHelper` `SpiderEntityHelper` `VexEntityHelper` `WitchEntityHelper` `ZombieVillagerEntityHelper` `AreaEffectCloudEntityHelper` `InteractionEntityHelper` `AbstractHorseEntityHelper` `AllayEntityHelper` `AxolotlEntityHelper` `BatEntityHelper` `BeeEntityHelper` `CatEntityHelper` `DolphinEntityHelper` `FishEntityHelper` `FoxEntityHelper` `FrogEntityHelper` `GoatEntityHelper` `HorseEntityHelper` `IronGolemEntityHelper` `LlamaEntityHelper` `MooshroomEntityHelper` `PandaEntityHelper` `ParrotEntityHelper` `PigEntityHelper` `RabbitEntityHelper` `SheepEntityHelper` `SnowGolemEntityHelper` `StriderEntityHelper` `TameableEntityHelper` `TropicalFishEntityHelper` `WolfEntityHelper` `ArrowEntityHelper` `FishingBobberEntityHelper` `TridentEntityHelper` `WitherSkullEntityHelper` `BoatEntityHelper` `FurnaceMinecartEntityHelper` `TntMinecartEntityHelper` `VillagerEntityHelper` `FChat` `FileHandler`
- 成员：`getKeyCode` `moveForward` `moveBackward` `moveStrafeLeft` `moveStrafeRight` `getWorldScanner` `toRelativePath` `toRawFile` `toRawPath` `getRawAttributes` `newInstance0` `createClassProxyBuilder` `getReflect` `getWidth` `getHeight` `drawLine` `register` `unregister` … 共 178 个名 / 230 个签名，全量见 `gap/tasks.json#G-004`
- 落点：`src/globals/**` `src/support/**` `docs/missing-apis.md` `tools/audit.mjs`
- 实施要点：最高 ROI：不新增 NeoMacros 面，只把 compat 的 UnimplementedError 桩改成桥接。230 = 216 个 compat stale + 14 个 A 类语义等价（世界扫描器、fs raw path、KeyBind keyCode、input movement、reflection getReflect 等）。
- 验收：npm test 20/20；npm run audit 完备性门禁绿；missing-apis.md 重新生成且对应条目消失；无 NeoMacros 源码改动。

### G-005 · compat 基线同步：CE 新增 39 成员 + 审计基线

- 阶段/优先级/规模/状态：W0 基线与设计 / P1 / S / 可认领
- 仓库：jsmacros-compat
- 依赖：`G-004`
- 范围类：`CraftingInventory` `FurnaceInventory` `PlayerInventory` `Vec3D` `RegistryHelper` `Draw3D` `ScriptScreen` `WorldScannerBuilder` `EventRecvPacket` `EventSendPacket` `EventSignEdit` `OptionsHelper` `PacketByteBufferHelper` `BlockStateHelper` `EntityHelper` `LivingEntityHelper` `BoatEntityHelper` `FClient` `FReflection`
- 成员：`getOutput` `getCraftingWidth` `getCraftingHeight` `getCraftingSlotCount` `to3D` `getNbtOps` `getWrapperLookup` `renderDepthPass` `renderAlwaysOnTopSurfaces` `renderDirect` `renderDirectSurfaces` `onClose` `isPauseScreen` `xorStateFilter` `xorBlockFilter` `xorStringBlockFilter` `xorStringStateFilter` `canGetPacketBuffer` … 共 30 个名 / 39 个签名，全量见 `gap/tasks.json#G-005`
- 落点：`tools/jsmacros-inventory.json` `tools/extract-jsmacros.mjs` `src/**` `docs/missing-apis.md`
- 实施要点：当前 compat 基线仍是 1.9.2 wiki 索引；dump 已补全 CE 源码。把 39 个 CE 新增成员纳入 inventory/audit，避免“旧报告看不到”的盲区。
- 验收：inventory 与 dump 的类/成员差集可复现；audit 覆盖新增成员；报告记录 1.9.2→CE 的差异。

### G-010 · FWorld 世界查询补全

- 阶段/优先级/规模/状态：W1 快速收益 / P0 / S / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`FWorld`
- 成员：`getRespawnPos` `getSkyLight` `getBlockLight` `playSoundFile`
- 落点：`core/api/world/WorldApi.java` `ports/WorldPort.java` `product/adapter/FabricWorldPort.java`
- 实施要点：getWorldScanner 走 G-004 回桥；本任务只做 getRespawnPos、getSkyLight、getBlockLight、playSoundFile。playSoundFile 需确认与 world.playSound（声音事件 id）的边界；光照需 26.x/1.21.6 双实现。
- 验收：4 个成员有 @ApiSince/@ApiMC/@ApiLoader；端口/适配器/StubAdapter 同步；core 单测 + e2e 脚本；四节点编译绿。

### G-012 · 实体基类状态面

- 阶段/优先级/规模/状态：W1 快速收益 / P0 / L / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`EntityHelper` `LivingEntityHelper` `PlayerEntityHelper` `ClientPlayerEntityHelper` `MobEntityHelper`
- 成员：`addVelocity` `addPos` `tryLookAt` `turnLeft` `turnRight` `turnBack` `getItemCooldownsRemainingTicks` `getItemCooldownRemainingTicks` `getTicksSinceCooldownsStart` `getTicksSinceCooldownStart` `calculateMiningSpeed` `isGlowing` `setCustomName` `setCustomNameVisible` `setGlowingColor` `resetGlowingColor` `getGlowingColor` `setGlowing` … 共 45 个名 / 52 个签名，全量见 `gap/tasks.json#G-012`
- 落点：`core/api/entity/EntityApi.java` `ports/EntityPort.java` `product/adapter/FabricEntityPort.java` `product/adapter/mixin/LocalPlayerMixin.java`
- 实施要点：EntityHelper: 发光/自定义名/速度（NBT 为 D）；LivingEntityHelper: 护甲槽位/触水呼吸/亡灵/getArmorToughness/isBaby/canSeeEntity；PlayerEntityHelper: 钓鱼浮标/攻击冷却/计分；ClientPlayer: calculateMiningSpeed + 冷却查询。部分成员是服务端-only（见 13 §2.3），先按 D 排除。
- 验收：客户端可观察成员全部落地；需要的私有 getter 通过 interface+@Accessor+neomacros$ 暴露；无 raw MC 类型泄漏；四节点/e2e 绿。

### G-013 · 背包事件 payload 字段

- 阶段/优先级/规模/状态：W1 快速收益 / P0 / S / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`EventClickSlot` `EventDropSlot` `EventOpenContainer` `EventContainerUpdate` `EventSlotUpdate`
- 成员：`getInventory` `inventory` `screen` `type`
- 落点：`core/event/def/inventory/*.java` `ports/model/*` `product/adapter/InventoryEventHubs.java` `docs`
- 实施要点：补 inventory/screen/type 等 payload 字段；能由现有 InventoryPort/事件 hub 提供的直接补，拿不到的登记 D。
- 验收：payload record 与事件目录同步；Mutable 面只暴露白名单字段；e2e 事件断言；docs 事件页重生成。

### G-014 · 玩家/实体事件 payload 字段

- 阶段/优先级/规模/状态：W1 快速收益 / P1 / M / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`EventArmorChange` `EventAttackBlock` `EventAttackEntity` `EventDamage` `EventDeath` `EventEXPChange` `EventFallFlying` `EventHeal` `EventHealthChange` `EventHeldItemChange` `EventHungerChange` `EventInteractBlock` `EventInteractEntity` `EventRiding` `EventStatusEffectUpdate` `EventEntityDamaged` `EventEntityHealed` `EventEntityLoad` `EventEntityUnload`
- 成员：`slot` `item` `oldItem` `side` `entity` `change` `respawn` `deathPos` `inventory` `total` `prevProgress` `prevTotal` `prevLevel` `state` `source` `offHand` `foodLevel` `offhand` … 共 25 个名 / 37 个签名，全量见 `gap/tasks.json#G-014`
- 落点：`core/event/def/player/*.java` `core/event/def/entity/*.java` `ports/model/*` `product/adapter/PortEventWiring.java`
- 实施要点：按事件逐个补字段；oldItem/newItem/source/attacker 等能由 PortEventWiring 快照差量提供的补，服务端-only 的登记 D。
- 验收：事件字段有 @ApiSince；Mutable 面白名单；e2e 至少覆盖每个事件的 1 个新字段；docs 重生成。

### G-015 · 聊天/世界/输入/屏幕事件字段

- 阶段/优先级/规模/状态：W1 快速收益 / P1 / S / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`EventRecvMessage` `EventSendMessage` `EventChunkLoad` `EventChunkUnload` `EventKey` `EventMouseScroll` `EventTitle` `EventSignEdit` `EventOpenScreen` `EventSound` `EventBlockUpdate` `EventBossbar` `EventJoinServer` `EventDimensionChange` `EventLaunchGame` `EventQuitGame` `EventDisconnect` `EventPlayerJoin` `EventPlayerLeave`
- 成员：`parse` `getKeyModifiers` `getModInt` `playerName` `deltaX` `deltaY` `signature` `messageType` `screenName` `pos` `signText` `block` `updateType` `bossBar` `type` `x` `z` `isFull` … 共 24 个名 / 28 个签名，全量见 `gap/tasks.json#G-015`
- 落点：`core/event/def/chat/*.java` `core/event/def/world/*.java` `core/event/def/input/*.java` `core/event/def/screen/*.java` `ports/model/*`
- 实施要点：x/z 由 ChunkPos 提供；messageType/signature/playerName 等先 triage；能补的补，拿不到的登记 D。
- 验收：同 G-014；chunk 坐标字段优先用 ChunkPos 句柄，不新增裸 x/z。

### G-016 · 缺失事件类型：资源包加载 / 名称变更

- 阶段/优先级/规模/状态：W1 快速收益 / P2 / S / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`EventResourcePackLoaded` `EventNameChange`
- 成员：`isGameStart` `loadedPacks` `entity` `oldName` `newName`
- 落点：`core/event/def/world/*.java` `core/event/def/entity/*.java` `ProductionEventSet` `product/adapter/*Hub.java`
- 实施要点：资源包加载事件需要 ResourceManager 回调；名称变更需要实体 customName 同步监听。两者都要评估高流量与去抖。
- 验收：事件注册/目录/文档/e2e 全套；命名 domain:camelCase；可取消性明确。

### G-017 · 数据类小缺口：方块/区块

- 阶段/优先级/规模/状态：W1 快速收益 / P1 / M / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`BlockDataHelper` `BlockHelper` `BlockStateHelper` `ChunkHelper` `HitResultHelper` `Block` `Entity`
- 成员：`getBlockState` `getRawBlock` `getRawBlockState` `getRawBlockEntity` `getDefaultState` `getDefaultItemStack` `canMobSpawnInside` `hasDynamicBounds` `getBlastResistance` `getJumpVelocityMultiplier` `getSlipperiness` `getVelocityMultiplier` `getTags` `hasTag` `getStates` `getId` `getName` `getBlock` … 共 48 个名 / 51 个签名，全量见 `gap/tasks.json#G-017`
- 落点：`core/api/types/Block.java` `BlockState.java` `Chunk.java` `RayHit.java` `ports/model/*`
- 实施要点：Block/BlockState/Chunk 的额外只读属性、HitResultHelper 的 Block/Entity 嵌套；Pos/Text/Plane 拆到 G-017B。
- 验收：签名无 raw MC 类型；DATA 类不可变；单元测试；docs 类页重生成。

### G-017B · 位置/文本/数学小批

- 阶段/优先级/规模/状态：W1 快速收益 / P1 / S / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`Pos3D` `Vec2D` `Vec3D` `TextBuilder` `StyleHelper` `Plane3D` `HitResultHelper` `Block` `Entity`
- 成员：`getNormalVector` `getVec12` `getVec13` `getVec23` `x1` `y1` `z1` `x2` `y2` `z2` `x3` `y3` `z3` `toRawBlockPos` `toMojangDoubleVector` `compareTo` `to3D` `withShowTextHover` … 共 35 个名 / 35 个签名，全量见 `gap/tasks.json#G-017B`
- 落点：`core/api/types/Pos3D.java` `Pos2D.java` `TextComponent.java` `RayHit.java` `ports/model/Pos3d.java`
- 实施要点：Pos3D.toRawBlockPos/toMojangDoubleVector、Vec2D.compareTo、Vec3D.to3D、TextBuilder hover/click、Plane3D 新类、HitResultHelper 的 Block/Entity 嵌套。Plane3D 先确认是否值得建类（否则 D）。
- 验收：数学边界测试；DATA 不可变；无 raw MC 类型；docs。

### G-018 · 数据句柄扩展：进度/统计

- 阶段/优先级/规模/状态：W1 快速收益 / P1 / M / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`AdvancementHelper` `AdvancementManagerHelper` `AdvancementProgressHelper` `StatsHelper`
- 成员：`getParent` `getChildren` `getRequirements` `getRequirementCount` `getExperience` `getLoot` `getRecipes` `toJson` `getAdvancementsForIdentifiers` `getStartedAdvancements` `getMissingAdvancements` `getCompletedAdvancements` `getRootAdvancements` `getSubAdvancements` `getAdvancement` `getAdvancementsProgress` `getAdvancementProgress` `isAnyObtained` … 共 44 个名 / 45 个签名，全量见 `gap/tasks.json#G-018`
- 落点：`core/api/player/PlayerApi.java` `ports/model/*`
- 实施要点：Advancement 的 requirements/loot/recipes、Stats 的 raw/formatted；数据字段优先，不新增写动作。
- 验收：DATA/HANDLE 分类正确；缺席=undefined；单元测试 + docs。

### G-018B · 数据句柄扩展：状态/计分/模组

- 阶段/优先级/规模/状态：W1 快速收益 / P1 / M / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`StatusEffectHelper` `ScoreboardObjectiveHelper` `ScoreboardsHelper` `BossBarHelper` `ModContainerHelper` `ServerInfoHelper` `PlayerListEntryHelper`
- 成员：`getId` `getName` `getDescription` `getVersion` `getEnv` `getAuthors` `getDependencies` `getPlayerScores` `scoreToDisplayName` `getTexts` `getKnownPlayers` `getKnownPlayersDisplayNames` `getDisplayName` `getTime` `isPermanent` `isAmbient` `hasIcon` `isVisible` … 共 47 个名 / 57 个签名，全量见 `gap/tasks.json#G-018B`
- 落点：`core/api/player/PlayerApi.java` `core/api/world/WorldApi.java` `core/api/client/ClientApi.java` `ports/model/*`
- 实施要点：状态效果 flag、计分板/队伍、BossBar、模组元数据、服务器信息、tab list 条目；优先补数据字段，不新增写动作。
- 验收：DATA/HANDLE 分类正确；缺席=undefined；单元测试 + docs。

### G-019 · 容器通用面

- 阶段/优先级/规模/状态：W1 快速收益 / P0 / M / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`Inventory` `PlayerInventory` `CraftingInventory` `RecipeInventory`
- 成员：`getCraftingWidth` `getCraftingHeight` `getCraftingSlotCount` `openGui` `getSlotUnderMouse` `getLocation` `getSlotPos` `getRawContainer` `getCurrentSyncId` `isInHotbar` `getOffhand` `getHelmet` `getChestplate` `getLeggings` `getBoots` `getInputSize` `getInput` `getCraftableRecipes` … 共 22 个名 / 29 个签名，全量见 `gap/tasks.json#G-019`
- 落点：`core/api/player/PlayerApi.java` `ports/InventoryPort.java` `product/adapter/FabricInventoryPort.java`
- 实施要点：鼠标槽位/槽位坐标/同步 id/raw container/openGui 先 triage；PlayerInventory 的 armor/offhand/hotbar 槽位与 Crafting/Recipe 的 grid/output 是重点。
- 验收：槽位语义有文档（含 1.21.6/26.x 差异）；写动作走 Promise；e2e 打开容器断言。

### G-020 · 容器菜单面

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P1 / L / 可认领
- 仓库：NeoMacros
- 依赖：`G-019`
- 范围类：`FurnaceInventory` `BrewingStandInventory` `AnvilInventory` `EnchantInventory` `BeaconInventory` `GrindStoneInventory` `SmithingInventory` `StoneCutterInventory`
- 成员：`getName` `setName` `getLevelCost` `getMaximumLevelCost` `selectFirstEffect` `selectSecondEffect` `applyEffects` `isBrewablePotion` `isValidIngredient` `isValidRecipe` `getMaxFuelUses` `canBrewCurrentInput` `getRemainingTicks` `previewPotion` `previewPotions` `getFirstPotion` `getSecondPotion` `getThirdPotion` … 共 39 个名 / 39 个签名，全量见 `gap/tasks.json#G-020`
- 落点：`core/api/player/PlayerApi.java` `ports/InventoryPort.java` `product/adapter/FabricInventoryPort.java`
- 实施要点：每类补 menu data/slots 的只读面（燃料/熔炼/酿造/铁砧 cost/附魔/信标/砂轮/锻造/切石）。写动作（select/enchant/apply）单列，默认 Promise。
- 验收：每个容器至少 1 个 e2e 打开+读取用例；菜单 data 缺失时 undefined，不抛；四节点。

### G-021 · 容器特化面：村民/马/织机/创造

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P1 / L / 可认领
- 仓库：NeoMacros
- 依赖：`G-019`
- 范围类：`VillagerInventory` `HorseInventory` `LoomInventory` `CreativeInventory`
- 成员：`scroll` `scrollTo` `getShownItems` `search` `selectSearch` `selectInventory` `selectHotbar` `selectTab` `getTabNames` `getTabTexts` `destroyHeldItem` `destroyAllItems` `setCursorStack` `saveHotbar` `restoreHotbar` `getSavedHotbar` `isInHotbar` `getOffhand` … 共 36 个名 / 36 个签名，全量见 `gap/tasks.json#G-021`
- 落点：`core/api/player/PlayerApi.java` `ports/InventoryPort.java` `product/adapter/FabricInventoryPort.java` `product/adapter/mixin/CreativeScreenAccessor.java`
- 实施要点：LoomInventory 当前无 NeoMacros 类（C）；CreativeInventory 22 项含 tab/搜索/hotbar 保存，需 CreativeModeInventoryScreen accessor；Villager/Horse 的 trades/saddle/armor 部分可能是 D（服务端/未公开）。
- 验收：Loom 类有 kind/wraps/构造；Creative 只读优先、写动作标风险；e2e 覆盖；docs 类树更新。

### G-022 · 物种姿态/展示面

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P1 / L / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`ArmorStandEntityHelper` `PandaEntityHelper` `DisplayEntityHelper` `TextDisplayDataHelper` `TextDisplayEntityHelper` `BlockDisplayEntityHelper` `ItemDisplayEntityHelper`
- 成员：`isVisible` `hasArms` `hasBasePlate` `getHeadRotation` `getBodyRotation` `getLeftArmRotation` `getRightArmRotation` `getLeftLegRotation` `getRightLegRotation` `getBlockState` `getLerpTargetY` `getLerpTargetZ` `getLerpTargetPitch` `getLerpTargetYaw` `getVisibilityBoundingBox` `getBillboardMode` `getTransform` `getLineWidth` … 共 31 个名 / 31 个签名，全量见 `gap/tasks.json#G-022`
- 落点：`core/api/entity/SpeciesEntities.java` `ports/EntityPort.java` `product/adapter/FabricEntityPort.java`
- 实施要点：ArmorStand 六向 pose、Panda gene、Display billboard/visibility、TextDisplay data 拆解、Block/ItemDisplay 的 blockState/transform。Lerp 插值器/亮度覆盖按 13 §2.3 标 D。
- 验收：需要 accessor 的走 interface+@Accessor；每类至少 1 个 e2e 实体断言；docs 类页。

### G-023 · 物种小缺口批（客户端可观察）

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P2 / L / 可认领
- 仓库：NeoMacros
- 依赖：`G-012`
- 范围类：`EnderDragonEntityHelper` `WitherEntityHelper` `EndCrystalEntityHelper` `ItemFrameEntityHelper` `PaintingEntityHelper` `DrownedEntityHelper` `EndermanEntityHelper` `GhastEntityHelper` `GuardianEntityHelper` `IllagerEntityHelper` `PillagerEntityHelper` `ShulkerEntityHelper` `SpellcastingIllagerEntityHelper` `WitchEntityHelper` `ZombieEntityHelper` `ZombieVillagerEntityHelper` `AreaEffectCloudEntityHelper` `FallingBlockEntityHelper` `TntEntityHelper` `AbstractHorseEntityHelper` `AllayEntityHelper` `AnimalEntityHelper` `AxolotlEntityHelper` `DolphinEntityHelper` `DonkeyEntityHelper` `FoxEntityHelper` `GoatEntityHelper` `LlamaEntityHelper` `MooshroomEntityHelper` `OcelotEntityHelper` `PufferfishEntityHelper` `RabbitEntityHelper` `TropicalFishEntityHelper` `WolfEntityHelper` `ArrowEntityHelper` `FishingBobberEntityHelper` `BoatEntityHelper` `TntMinecartEntityHelper` `VillagerEntityHelper`
- 成员：`getBodyPart` `getBodyParts` `getRemainingInvulnerableTime` `isFirstPhase` `isSecondPhase` `isNatural` `isGlowingFrame` `getIdentifier` `hasTrident` `hasNautilusShell` `isProvoked` `getHeldBlock` `isShooting` `isElder` `hasSpikesRetracted` `isCelebrating` `getState` `isCaptain` … 共 61 个名 / 65 个签名，全量见 `gap/tasks.json#G-023`
- 落点：`core/api/entity/SpeciesEntities.java` `ports/EntityPort.java` `product/adapter/FabricEntityPort.java`
- 实施要点：自动收纳未进 G-022 的物种类；先按 13 §2.3 排除服务端-only，再逐成员补客户端可观察值（变体/状态/物品/姿态）。
- 验收：每类成员有 A/B/D 结论；B 类落地并测试；D 类回写 13 §2.3。

### G-024 · Options 根面 + Video

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P1 / L / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`OptionsHelper` `VideoOptionsHelper`
- 成员：`getSkinOptions` `getVideoOptions` `getMusicOptions` `getControlOptions` `getChatOptions` `getAccessibilityOptions` `saveOptions` `getResourcePacks` `getEnabledResourcePacks` `setEnabledResourcePacks` `removeServerResourcePack` `getLanguage` `setLanguage` `isDifficultyLocked` `lockDifficulty` `unlockDifficulty` `getFov` `setFov` … 共 81 个名 / 81 个签名，全量见 `gap/tasks.json#G-024`
- 落点：`core/api/client/ClientApi.java` `ports/ClientPort.java` `ports/model/OptionsData.java` `product/adapter/FabricClientPort.java`
- 实施要点：当前 client.options 只有 fov/gamma/guiScale/renderDistance；补根面 + video/graphics 读写。OptionsKeyMappingsAccessor 已有，键位部分复用。
- 验收：读写成对；写走 Promise 且回读校验；1.21.6/26.x 字段差异用 Stonecutter 门；docs 页。

### G-024B · Options：Chat / Control / Accessibility

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P1 / L / 可认领
- 仓库：NeoMacros
- 依赖：`G-024`
- 范围类：`ChatOptionsHelper` `ControlOptionsHelper` `AccessibilityOptionsHelper`
- 成员：`getParent` `getNarratorMode` `setNarratorMode` `areSubtitlesShown` `showSubtitles` `setTextBackgroundOpacity` `getTextBackgroundOpacity` `isBackgroundForChatOnly` `enableBackgroundForChatOnly` `getChatOpacity` `setChatOpacity` `getChatLineSpacing` `setChatLineSpacing` `getChatDelay` `setChatDelay` `isAutoJumpEnabled` `enableAutoJump` `isSneakTogglingEnabled` … 共 67 个名 / 89 个签名，全量见 `gap/tasks.json#G-024B`
- 落点：`core/api/client/ClientApi.java` `ports/ClientPort.java` `ports/model/OptionsData.java` `product/adapter/FabricClientPort.java`
- 实施要点：聊天可见性/颜色/链接、控制灵敏度/反转/触屏/raw input、无障碍 narrator/字幕/背景透明度等。
- 验收：同 G-024；控制选项复用 OptionsKeyMappingsAccessor。

### G-024C · Options：Music / Skin

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P2 / M / 可认领
- 仓库：NeoMacros
- 依赖：`G-024`
- 范围类：`MusicOptionsHelper` `SkinOptionsHelper`
- 成员：`getParent` `getMasterVolume` `setMasterVolume` `getMusicVolume` `setMusicVolume` `getRecordsVolume` `setRecordsVolume` `getWeatherVolume` `setWeatherVolume` `getBlocksVolume` `setBlocksVolume` `getHostileVolume` `setHostileVolume` `getNeutralVolume` `setNeutralVolume` `getPlayerVolume` `setPlayerVolume` `getAmbientVolume` … 共 45 个名 / 47 个签名，全量见 `gap/tasks.json#G-024C`
- 落点：`core/api/client/ClientApi.java` `ports/ClientPort.java` `ports/model/OptionsData.java` `product/adapter/FabricClientPort.java`
- 实施要点：音量分组/设备、披风/皮肤部件开关。
- 验收：同 G-024。

### G-025 · RegistryHelper 全谱

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P1 / L / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`RegistryHelper`
- 成员：`getNbtOps` `getWrapperLookup` `getItem` `getItemStack` `getItemIds` `getItems` `getBlock` `getBlockState` `getStatusEffect` `getStatusEffects` `getBlockIds` `getBlocks` `getEnchantment` `getEnchantmentIds` `getEntity` `getRawEntityType` `getEntityTypeIds` `getFluidState` … 共 39 个名 / 42 个签名，全量见 `gap/tasks.json#G-025`
- 落点：`core/api/client/ClientApi.java` `ports/ClientPort.java` `product/adapter/FabricClientPort.java`
- 实施要点：client.registryIds/registryLookup 已有；补类型化 getItem/getBlock/getEntityType/enchantment/effect/particle 等与 id 解析。只读，无写面。
- 验收：类型化返回 DATA/句柄；未知 id=undefined；单元测试 + docs。

### G-026 · 命令构建面：CommandBuilder

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P2 / L / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`CommandBuilder`
- 成员：`literalArg` `booleanArg` `intArg` `intRangeArg` `longArg` `floatRangeArg` `doubleArg` `uuidArgType` `greedyStringArg` `quotedStringArg` `wordArg` `regexArgType` `textArgType` `timeArg` `identifierArg` `nbtArg` `nbtElementArg` `nbtCompoundArg` … 共 39 个名 / 48 个签名，全量见 `gap/tasks.json#G-026`
- 落点：`core/api/CommandApi.java` `core/api/CommandArg.java` `core/api/CommandSpec.java` `product/adapter/FabricCommandPort.java`
- 实施要点：CommandBuilder 是 JsMacros 的链式 builder；NeoMacros 走 CommandSpec 声明式。先 triage：能映射的做兼容工厂，不能映射的 D。
- 验收：不新增重复 builder 体系；能映射的成员有 e2e；D 类写明理由。

### G-026B · 命令构建面：Manager / Suggestions / Node / Source

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P2 / M / 可认领
- 仓库：NeoMacros
- 依赖：`G-026`
- 范围类：`CommandManager` `SuggestionsBuilderHelper` `CommandNodeHelper`
- 成员：`getValidCommands` `createCommandBuilder` `unregisterCommand` `reRegisterCommand` `getArgumentAutocompleteOptions` `fabric` `getInput` `getStart` `getRemainingLowerCase` `suggest` `suggestWithTooltip` `suggestMatching` `suggestIdentifier` `suggestBlockPositions` `suggestPositions`
- 落点：`core/api/CommandApi.java` `core/api/CommandSpec.java` `product/adapter/FabricCommandPort.java`
- 实施要点：命令列表/注销/建议/节点/source；能映射到 command.register/execute/suggest 的补，内部派发实现登记 D。
- 验收：同 G-026。

### G-027 · 物品面：ItemStack / Item

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P1 / L / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`ItemStackHelper` `ItemHelper`
- 成员：`getCreativeTab` `getGroupIcon` `canBeRepairedWith` `isSuitableFor` `isBlockItem` `getBlock` `getMiningSpeedMultiplier` `hasRecipeRemainder` `getRecipeRemainder` `getEnchantability` `getMaxDurability` `isFireproof` `isTool` `isWearable` `getFood` `canBeNested` `getDefaultStack` `canBeApplied` … 共 35 个名 / 41 个签名，全量见 `gap/tasks.json#G-027`
- 落点：`core/api/types/ItemStack.java` `ItemStackBuilder.java` `ports/model/ItemStackData.java`
- 实施要点：补 NBT 以外可观察面：maxCount、cooldown、destroy/place restrictions、creative tab、tool/wearable 判定。NBT 族 D。
- 验收：DATA 字段扩展；Builder 写面 Promise；未知=undefined；docs。

### G-027B · 物品面：附魔 / 食物 / 创造

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P1 / M / 可认领
- 仓库：NeoMacros
- 依赖：`G-027`
- 范围类：`EnchantmentHelper` `FoodComponentHelper` `CreativeItemStackHelper`
- 成员：`setDurability` `addEnchantment` `clearEnchantments` `removeEnchantment` `clearLore` `addLore` `hideEnchantments` `hideModifiers` `hideUnbreakable` `hideCanDestroy` `hideCanPlace` `hideDye` `getLevel` `getMinLevel` `getMaxLevel` `getRomanLevelName` `getConflictingEnchantments` `getCompatibleEnchantments` … 共 28 个名 / 36 个签名，全量见 `gap/tasks.json#G-027B`
- 落点：`core/api/types/ItemStack.java` `ports/model/EnchantmentData.java`
- 实施要点：附魔兼容/等级/诅咒/宝藏、食物属性、创造物品附魔/隐藏标志。
- 验收：同 G-027。

### G-028 · 交互/目标面

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P1 / L / 可认领
- 仓库：NeoMacros
- 依赖：`G-012`
- 范围类：`InteractionManagerHelper` `Target` `Break` `Interact` `BreakBlockResult` `InteractionProxy`
- 成员：`reset` `setOverride` `addCallback` `isBreaking` `onBreakBlock` `reason` `pos` `isInteracting` `ensureInteracting` `resetChecks` `setTargetBlock` `setTarget` `setTargetMissed` `hasOverride` `onUpdate` `isInRange` `checkBase` `setGameMode` … 共 33 个名 / 49 个签名，全量见 `gap/tasks.json#G-028`
- 落点：`core/api/player/PlayerApi.java` `ports/PlayerPort.java`
- 实施要点：Target override/checks、Break/Interact 回调和状态；与现有 player.target/interactBlock/attackBlock 去重。
- 验收：动作走 Promise；只读状态属性化；override 语义文档化；e2e。

### G-028B · 输入动作面：PlayerInput / KeyTracker

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P1 / S / 可认领
- 仓库：NeoMacros
- 依赖：`G-028`
- 范围类：`PlayerInput` `KeyTracker`
- 成员：`fromCsv` `fromJson` `clone` `movementForward` `movementSideways` `yaw` `pitch` `jumping` `sneaking` `sprinting` `unpress`
- 落点：`core/api/input/InputApi.java` `ports/model/MovementSpec.java`
- 实施要点：PlayerInput 的 CSV/JSON/clone/movement 字段；KeyTracker 的按压状态。能映射到 input.movement/pressedKeys 的补，其余 D。
- 验收：同 G-028。

### G-029 · 方块状态类型化查询

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P2 / L / 可认领
- 仓库：NeoMacros
- 依赖：无
- 范围类：`UniversalBlockStateHelper`
- 成员：`getAttachment` `getEastWallShape` `getNorthWallShape` `getSouthWallShape` `getWestWallShape` `getEastWireConnection` `getNorthWireConnection` `getSouthWireConnection` `getWestWireConnection` `getBlockHalf` `getDoubleBlockHalf` `getRailShape` `getStraightRailShape` `getOrientation` `getHorizontalAxis` `getAxis` `getHorizontalFacing` `getHopperFacing` … 共 99 个名 / 99 个签名，全量见 `gap/tasks.json#G-029`
- 落点：`core/api/types/BlockState.java` `ports/model/BlockStateData?` `product/adapter/FabricWorldPort.java`
- 实施要点：110 个类型化 getter（facing/axis/rail/wire/honey/level...）。不要逐个硬编码 110 个方法；先设计 state property 访问器 + 常用便捷属性，其余走 props 映射。
- 验收：常用 20-30 个便捷成员 + 通用 props 查询；未知属性=undefined；版本差异表；docs。

### G-030 · CustomImage 绘制面

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P2 / M / 可认领
- 仓库：NeoMacros
- 依赖：`G-001`
- 范围类：`CustomImage`
- 成员：`getName` `loadImage` `update` `saveImage` `getIdentifier` `getImage` `getPixel` `setPixel` `drawImage` `getGraphicsColor` `setGraphicsColor` `translate` `clipRect` `setClip` `setPaintMode` `setXorMode` `getClipBounds` `copyArea` … 共 36 个名 / 40 个签名，全量见 `gap/tasks.json#G-030`
- 落点：`core/api/types/Image.java` `core/api/HudApi.java` `product/adapter/FabricHudRegistry.java`
- 实施要点：像素读写/绘制/裁剪/文字/纹理注册；与 hud.layer 元素模型绑定。若 G-001 决定不建 texture 层，则本任务缩水为 Image DATA 只读。
- 验收：像素操作有边界测试；上传/注册走游戏主线程；异步 Promise；docs。

### G-031 · 包级 API（packet）

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / XL / 阻塞
- 仓库：NeoMacros
- 依赖：`G-002`
- 范围类：`PacketByteBufferHelper` `EventRecvPacket` `EventSendPacket` `FClient`
- 成员：`canGetPacketBuffer` `getPacketBuffer` `packet` `type` `replacePacket` `toPacket` `getPacketId` `getNetworkStateId` `isClientbound` `isServerbound` `sendPacket` `receivePacket` `getPacketNames` `reset` `writeRegistryKey` `readRegistryKey` `writeCollection` `readList` … 共 131 个名 / 157 个签名，全量见 `gap/tasks.json#G-031`
- 落点：`core/event/def/packet/*.java` `ports/ClientPort.java` `product/adapter/mixin/ClientPacketListenerMixin.java`
- 实施要点：T-517 后置；G-002 设计确认后才能开工。高流量包忽略名单、send 信任门、缓冲区生命周期必须先定。
- 验收：事件/缓冲区/过滤/权限全套；真机验证；docs。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-032 · Draw2D 容器与图层

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / M / 阻塞
- 仓库：NeoMacros
- 依赖：`G-001`
- 范围类：`Draw2D` `IDraw2D`
- 成员：`getWidth` `getHeight` `getDraw2Ds` `addDraw2D` `removeDraw2D` `addImage` `addRect` `addLine` `removeLine` `addItem` `init` `getElementsByZIndex` `setZIndex` `getZIndex` `widthSupplier` `heightSupplier` `zIndex` `visible` … 共 29 个名 / 97 个签名，全量见 `gap/tasks.json#G-032`
- 落点：`core/api/HudApi.java` `core/api/render/*` `product/adapter/FabricHudRegistry.java`
- 实施要点：持久元素/图层/父子/缩放/z-index；Draw2D/IDraw2D 容器面。Draw2DElement 拆到 G-032B。
- 验收：元素生命周期测试；每帧渲染；docs。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-032B · Draw2DElement 基类与 Builder

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / M / 阻塞
- 仓库：NeoMacros
- 依赖：`G-032`
- 范围类：`Draw2DElement` `Builder`
- 成员：`getDraw2D` `setX` `getX` `setY` `getY` `setPos` `setWidth` `getWidth` `setHeight` `getHeight` `setSize` `setScale` `getScale` `setRotation` `getRotation` `setRotateCenter` `isRotatingCenter` `setZIndex` … 共 40 个名 / 63 个签名，全量见 `gap/tasks.json#G-032B`
- 落点：`core/api/render/components/Draw2DElement.java` `product/adapter/FabricHudRegistry.java`
- 实施要点：元素基类 + Builder；作为 G-034/034B/034C 的父类先落地。
- 验收：基类 API 稳定；继承测试；docs。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-033 · Draw3D 与基础 3D 组件

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / M / 阻塞
- 仓库：NeoMacros
- 依赖：`G-001`
- 范围类：`Draw3D` `Box` `Line3D` `RenderElement3D` `Builder`
- 成员：`setPos` `setPosToBlock` `setPosToPoint` `setColor` `setFillColor` `setAlpha` `setFillAlpha` `setFill` `compareToSame` `pos` `color` `fillColor` `fill` `cull` `pos1` `getPos1` `pos2` `getPos2` … 共 45 个名 / 117 个签名，全量见 `gap/tasks.json#G-033`
- 落点：`core/api/render/RenderApi.java` `ports/Draw3DPort.java` `product/adapter/FabricDraw3DPort.java`
- 实施要点：box/line 已有原语；补 Draw3D 容器与 Box/Line3D 组件及 Builder。Surface/TraceLine 拆到 G-033B。
- 验收：组件与现有原语不重复；e2e/截图验证；docs。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-033B · 3D 面/追踪线组件

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / M / 阻塞
- 仓库：NeoMacros
- 依赖：`G-033`
- 范围类：`Surface` `TraceLine` `EntityTraceLine` `Builder`
- 成员：`setEntity` `setYOffset` `render` `entity` `yOffset` `getEntity` `getYOffset` `color` `getColor` `alpha` `alwaysOnTop` `getAlpha` `buildAndAdd` `build` `setPos` `bindToEntity` `getBoundEntity` `setBoundOffset` … 共 68 个名 / 120 个签名，全量见 `gap/tasks.json#G-033B`
- 落点：`core/api/render/RenderApi.java` `ports/Draw3DPort.java` `product/adapter/FabricDraw3DPort.java`
- 实施要点：Surface/TraceLine/EntityTraceLine 及 Builder；复用 G-033 的组件基类。
- 验收：同 G-033。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-034 · 2D 组件：Text / Rect / Line

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / L / 阻塞
- 仓库：NeoMacros
- 依赖：`G-032`
- 范围类：`Text` `Rect` `Line` `Builder`
- 成员：`setX1` `getX1` `setY1` `getY1` `setPos1` `setX2` `getX2` `setY2` `getY2` `setPos2` `setPos` `setColor` `getColor` `setAlpha` `getAlpha` `setRotation` `getRotation` `setRotateCenter` … 共 67 个名 / 233 个签名，全量见 `gap/tasks.json#G-034`
- 落点：`core/api/render/components/*` `core/api/HudApi.java` `product/adapter/FabricHudRegistry.java`
- 实施要点：Text/Rect/Line 及 Builder；任务卡列 67 个成员名、233 个签名，先做核心绘制/属性，再补 Builder 链。
- 验收：每个组件有 add/remove/属性/Builder 测试；docs 类页。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-034B · 2D 组件：Image / Item

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / M / 阻塞
- 仓库：NeoMacros
- 依赖：`G-032`
- 范围类：`Image` `Item` `Builder`
- 成员：`setImage` `getImage` `setX` `getX` `setY` `getY` `setPos` `setWidth` `getWidth` `setHeight` `getHeight` `setSize` `setColor` `getColor` `getAlpha` `setRotation` `getRotation` `setRotateCenter` … 共 77 个名 / 165 个签名，全量见 `gap/tasks.json#G-034B`
- 落点：`core/api/render/components/*` `core/api/HudApi.java` `product/adapter/FabricHudRegistry.java`
- 实施要点：Image/Item 及 Builder；Image 与 G-030 CustomImage 的纹理面共用底层。
- 验收：同 G-034。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-034C · 2D 组件基类：Alignable / RenderElement

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / S / 阻塞
- 仓库：NeoMacros
- 依赖：`G-032`
- 范围类：`Alignable` `RenderElement` `RenderElementBuilder`
- 成员：`alignHorizontally` `alignVertically` `align` `moveTo` `moveToX` `moveToY` `getScaledWidth` `getParentWidth` `getScaledHeight` `getParentHeight` `getScaledLeft` `getScaledTop` `getScaledRight` `getScaledBottom` `parsePercentage` `getZIndex` `render` `build` … 共 19 个名 / 28 个签名，全量见 `gap/tasks.json#G-034C`
- 落点：`core/api/render/components/*`
- 实施要点：对齐/父容器/缩放/z-index 的基类；先于 G-034/034B 落地。
- 验收：基类 API 稳定；组件继承测试；docs。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-035 · 脚本 GUI 核心

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / L / 阻塞
- 仓库：NeoMacros
- 依赖：`G-003`
- 范围类：`ScriptScreen` `IScreen`
- 成员：`getScreenClassName` `getTitleText` `getButtonWidgets` `getTextFields` `addButton` `addCheckbox` `addSlider` `addLockButton` `addCyclingButton` `addTextInput` `setOnMouseDown` `setOnMouseDrag` `setOnMouseUp` `setOnKeyPressed` `setOnCharTyped` `setOnClose` `close` `reloadScreen` … 共 37 个名 / 52 个签名，全量见 `gap/tasks.json#G-035`
- 落点：`core/api/player/PlayerApi.java` `product/fabric/ScriptScreen.java` `product/fabric/NeoMacrosFabric.java`
- 实施要点：T-521 P3；脚本自建 Screen 的 open/close/onRender/onClose。
- 验收：Screen 生命周期/回调/关闭语义；e2e 打不开也不崩；docs。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-036 · 脚本 GUI 控件：基础四类

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / L / 阻塞
- 仓库：NeoMacros
- 依赖：`G-035`
- 范围类：`ButtonWidgetHelper` `ButtonBuilder` `TexturedButtonBuilder` `CheckBoxWidgetHelper` `CheckBoxBuilder` `SliderWidgetHelper` `SliderBuilder` `TextFieldWidgetHelper` `TextFieldBuilder`
- 成员：`height` `size` `getAction` `action` `createWidget` `enabledTexture` `disabledTexture` `enabledFocusedTexture` `disabledFocusedTexture` `isChecked` `toggle` `setChecked` `checked` `getValue` `setValue` `getSteps` `setSteps` `steps` … 共 37 个名 / 62 个签名，全量见 `gap/tasks.json#G-036`
- 落点：`core/api/render/screen/*` `product/fabric/ScriptScreen.java`
- 实施要点：先做 Button/CheckBox/Slider/TextField 四类控件与 Builder；其余控件拆到 G-036B。
- 验收：控件状态/回调/布局测试；docs。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-036B · 脚本 GUI 控件：次级控件

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / M / 阻塞
- 仓库：NeoMacros
- 依赖：`G-036`
- 范围类：`AbstractWidgetBuilder` `CyclingButtonWidgetHelper` `CyclicButtonBuilder` `LockButtonWidgetHelper` `LockButtonBuilder` `ClickableWidgetHelper`
- 成员：`getWidth` `width` `getHeight` `height` `size` `getZIndex` `zIndex` `getX` `x` `getY` `y` `pos` `getMessage` `message` `isActive` `active` `isVisible` `visible` … 共 64 个名 / 86 个签名，全量见 `gap/tasks.json#G-036B`
- 落点：`core/api/render/screen/*` `product/fabric/ScriptScreen.java`
- 实施要点：Cycling/Lock/Clickable 与抽象 Builder 基类；在基础四类稳定后补。
- 验收：同 G-036。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-037 · FPlayer P3：签名/全景

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / S / 阻塞
- 仓库：NeoMacros
- 依赖：`G-003`
- 范围类：`FPlayer`
- 成员：`writeSign` `takePanorama`
- 落点：`core/api/player/PlayerApi.java` `product/adapter/FabricPlayerPort.java` `product/fabric/NeoMacrosFabric.java`
- 实施要点：writeSign 依赖 SignScreen/服务器签名包；takePanorama 依赖截图/全景管线（T-520 P3）。
- 验收：真机验证；失败语义明确；docs。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-038 · 纹理/注册纹理面

- 阶段/优先级/规模/状态：W3 渲染/GUI/包级 / P2 / M / 阻塞
- 仓库：NeoMacros
- 依赖：`G-001`
- 范围类：`FHud`
- 成员：`createScreen` `openScreen` `createTexture` `getRegisteredTextures`
- 落点：`core/api/HudApi.java` `core/api/types/Image.java` `product/adapter/FabricHudRegistry.java`
- 实施要点：FHud.createTexture/getRegisteredTextures；T-518 后置。与 G-030 的 Image 纹理注册合并评审。
- 验收：纹理生命周期/去重/清理；docs。
- 阻塞说明：先完成依赖的设计/前置任务，不要直接写实现。

### G-039 · 世界扫描 DSL 对齐（compat）

- 阶段/优先级/规模/状态：W2 数据/容器/物种 / P1 / L / 可认领
- 仓库：jsmacros-compat
- 依赖：`G-004`
- 范围类：`WorldScanner` `WorldScannerBuilder`
- 成员：`getChunkRange` `scanAroundPlayer` `scanChunkRange` `scanCubeArea` `scanCubeAreaInclusive` `scanSphereArea` `scanReachable` `scanClosestReachable` `getBlocksInChunk` `getBlocksInChunks` `getCachedAmount` `withStateFilter` `andStateFilter` `orStateFilter` `xorStateFilter` `notStateFilter` `withBlockFilter` `andBlockFilter` … 共 36 个名 / 49 个签名，全量见 `gap/tasks.json#G-039`
- 落点：`src/globals/world.js` `src/support/surface-data.js` `tools/audit.mjs`
- 实施要点：NeoMacros 用 world.findBlocks/scanBlocks options DSL；不新建 JsMacros filter 类。把 WorldScanner/Builder 的成员映射到 options 对象，filter 类保持 D/桩。
- 验收：扫描/过滤/排序/上限语义对齐；npm test；missing-apis 更新。

### G-040 · 永久缺口（不实施）

- 阶段/优先级/规模/状态：不实施 / — / — / 不实施
- 仓库：NeoMacros
- 依赖：无
- 范围类：`ClassBuilder`
- 成员：无（不实施）
- 落点：见 notes
- 实施要点：运行时 Java 编译/动态类生成/Jar 加载（ClassBuilder 族 + CombinedVariableClassLoader + FReflection compile 族）。按 13 §2.1 拍板④不实施；脚本用 neo.java/neo.reflect 覆盖。
- 验收：无代码；若出现新证据，先在 13 号重新拍板。
- 不实施：只保留记录；若出现新证据，先回 13 号重新拍板。

## 5. 排除项（不要开发）

### 5.1 D：不映射 / 服务端-only / 既定反模式（241 成员）

| 类 | 成员数 | 代表成员 | 理由 |
| --- | ---: | --- | --- |
| `ChatHistoryManager` | 19 | `getRecvLine` `getRecvCount` `getRecvLines` `insertRecvText` `removeRecvText` `removeRecvTextMatching` `removeRecvTextMatchingFilter` `refreshVisible` … | chat history and command management are not exposed by NeoMacros |
| `FJsMacros` | 17 | `getProfile` `getConfig` `getServiceManager` `getOpenContexts` `runScript` `wrapScriptRun` `wrapScriptRunAsync` | NeoMacros has no profile concept |
| `FPlayer` | 16 | `setGameMode` `createPlayerInput` `createPlayerInputsFromCsv` `createPlayerInputsFromJson` `getCurrentPlayerInput` `addInput` `addInputs` `clearInputs` … | NeoMacros exposes no gamemode write |
| `TeamHelper` | 15 | `getName` `getDisplayName` `getPlayerList` `getColorFormat` `getColorIndex` `getColorValue` `getColorName` `getScoreboard` … | NeoMacros 无对应类/模块/事件 |
| `NBTElementHelper` | 13 | `getType` `resolve` `isNull` `isNumber` `isString` `isCompound` `asString` `asNumberHelper` … | NeoMacros 无对应类/模块/事件 |
| `AbstractHorseEntityHelper` | 13 | `canWearArmor` `canBeSaddled` `getJumpStrengthStat` `getHorseJumpHeight` `getMaxJumpStrengthStat` `getMinJumpStrengthStat` `getSpeedStat` `getHorseSpeed` … | specialized entity state has no equivalent in the neo entity surface |
| `FakeServerCommandSource` | 10 | `getSelectedEntities` `getCustomTabSuggestions` `getOnlinePlayerNames` `getAllTeams` `getAvailableSounds` `customSuggestion` `getRelevantCoordinates` `getAbsoluteCoordinates` … | NeoMacros 未暴露同名或等价成员 |
| `NBTNumberHelper` | 7 | `asLong` `asInt` `asShort` `asByte` `asFloat` `asDouble` `asNumber` | NeoMacros 无对应类/模块/事件 |
| `FoxEntityHelper` | 7 | `getOwner` `getSecondOwner` `canTrust` `hasFoundTarget` `isWandering` `isDefending` `isJumping` | specialized entity state has no equivalent in the neo entity surface |
| `FluidStateHelper` | 7 | `getId` `isStill` `getHeight` `getLevel` `getVelocity` `getBlockState` `getBlastResistance` | NeoMacros 无对应类/模块/事件 |
| `RecipeHelper` | 6 | `getIngredients` `craft` `getGroup` `canCraft` `getCraftableAmount` | NeoMacros 无对应类/模块/事件 |
| `WardenEntityHelper` | 6 | `getAnger` `isDigging` `isEmerging` `isRoaring` `isSniffing` `isChargingSonicBoom` | specialized entity state has no equivalent in the neo entity surface |
| `StringifyFilter` | 5 | `addOption` `removeOption` `apply` | NeoMacros 无对应类/模块/事件 |
| `DyeColorHelper` | 5 | `getName` `getId` `getColorValue` `getFireworkColor` `getSignColor` | NeoMacros 无对应类/模块/事件 |
| `FormattingHelper` | 5 | `getColorValue` `getColorIndex` `getName` `isColor` `isModifier` | NeoMacros 无对应类/模块/事件 |
| `NBTListHelper` | 5 | `isPossiblyUUID` `asUUID` `length` `get` `getHeldType` | NeoMacros 无对应类/模块/事件 |
| `IAdvancedFilter` | 4 | `and` `or` `xor` `not` | NeoMacros 无对应类/模块/事件 |
| `BasicFilter` | 4 | `and` `or` `xor` `not` | NeoMacros 无对应类/模块/事件 |
| `BlockPredicateHelper` | 4 | `getBlocks` `getStatePredicate` `getNbtPredicate` `test` | NeoMacros 无对应类/模块/事件 |
| `NBTCompoundHelper` | 4 | `getKeys` `getType` `get` `asString` | NeoMacros 无对应类/模块/事件 |
| `DisplayEntityHelper` | 4 | `getLerpTargetX` `getSkyBrightness` `getBlockBrightness` `getLerpProgress` | specialized entity state has no equivalent in the neo entity surface |
| `CreeperEntityHelper` | 4 | `getFuseChange` `getFuseTime` `getMaxFuseTime` `getRemainingFuseTime` | specialized entity state has no equivalent in the neo entity surface |
| `PiglinEntityHelper` | 4 | `isWandering` `isAdmiring` `isMeleeAttacking` `hasCrossbowReady` | specialized entity state has no equivalent in the neo entity surface |
| `GroupFilter` | 3 | `add` `getFilters` | NeoMacros 无对应类/模块/事件 |
| `AndFilter` | 3 | `apply` `getFilterOne` `getFilterTwo` | NeoMacros 无对应类/模块/事件 |
| `OrFilter` | 3 | `apply` `getFilterOne` `getFilterTwo` | NeoMacros 无对应类/模块/事件 |
| `XorFilter` | 3 | `apply` `getFilterOne` `getFilterTwo` | NeoMacros 无对应类/模块/事件 |
| `NbtPredicateHelper` | 3 | `test` | NeoMacros 无对应类/模块/事件 |
| `ChatHudLineHelper` | 3 | `getText` `getCreationTick` `deleteById` | NeoMacros 未暴露同名或等价成员 |
| `MerchantEntityHelper` | 3 | `refreshTrades` `getExperience` `hasCustomer` | specialized entity state has no equivalent in the neo entity surface |
| `ClassWrapperFilter` | 2 | `getFilter` `apply` | NeoMacros 无对应类/模块/事件 |
| `NotFilter` | 2 | `apply` `getFilter` | NeoMacros 无对应类/模块/事件 |
| `StatePredicateHelper` | 2 | `test` | NeoMacros 无对应类/模块/事件 |
| `InteractionEntityHelper` | 2 | `setCanHit` `shouldRespond` | specialized entity state has no equivalent in the neo entity surface |
| `ParrotEntityHelper` | 2 | `isFlying` `isSittingOnShoulder` | specialized entity state has no equivalent in the neo entity surface |
| `StateHelper` | 2 | `toMap` `with` | NeoMacros 无对应类/模块/事件 |
| `FChat` | 2 | `getLogger` | NeoMacros exposes no logger factory |
| `IFilter` | 1 | `apply` | NeoMacros 无对应类/模块/事件 |
| `BooleanCompareFilter` | 1 | `apply` | NeoMacros 无对应类/模块/事件 |
| `CharCompareFilter` | 1 | `apply` | NeoMacros 无对应类/模块/事件 |
| `NumberCompareFilter` | 1 | `apply` | NeoMacros 无对应类/模块/事件 |
| `StringCompareFilter` | 1 | `apply` | NeoMacros 无对应类/模块/事件 |
| `FilterMethod` | 1 | `getMethod` | NeoMacros 无对应类/模块/事件 |
| `AllMatchFilter` | 1 | `apply` | NeoMacros 无对应类/模块/事件 |
| `AnyMatchFilter` | 1 | `apply` | NeoMacros 无对应类/模块/事件 |
| `CountMatchFilter` | 1 | `apply` | NeoMacros 无对应类/模块/事件 |
| `NoneMatchFilter` | 1 | `apply` | NeoMacros 无对应类/模块/事件 |
| `ItemHelper` | 1 | `getStackWithNbt` | NeoMacros 未暴露同名或等价成员 |
| `ItemStackHelper` | 1 | `getNBT` | not exposed by the neo item stack surface |
| `BlockDataHelper` | 1 | `getNBT` | no equivalent in the neo block surface |
| `EntityHelper` | 1 | `getNBT` | entity state or write action not exposed by the neo entity surface |
| `MobEntityHelper` | 1 | `isAttacking` | specialized entity state has no equivalent in the neo entity surface |
| `TextDisplayEntityHelper` | 1 | `getData` | specialized entity state has no equivalent in the neo entity surface |
| `AbstractPiglinEntityHelper` | 1 | `canBeZombified` | specialized entity state has no equivalent in the neo entity surface |
| `EndermanEntityHelper` | 1 | `isHoldingBlock` | specialized entity state has no equivalent in the neo entity surface |
| `VindicatorEntityHelper` | 1 | `isJohnny` | specialized entity state has no equivalent in the neo entity surface |
| `AllayEntityHelper` | 1 | `canDuplicate` | specialized entity state has no equivalent in the neo entity surface |
| `TridentEntityHelper` | 1 | `hasLoyalty` | specialized entity state has no equivalent in the neo entity surface |
| `FClient` | 1 | `getRegistryManager` | NeoMacros exposes no registry data (java escape layer only) |

> D 的完整判定见 13 号 §2.2/§2.3；本表只做执行层汇总。

### 5.2 E：永久缺口（129 成员）

| 类 | 成员数 | 代表成员 | 理由 |
| --- | ---: | --- | --- |
| `AnnotationBuilder` | 14 | `putString` `putBoolean` `putByte` `putChar` `putShort` `putInt` `putLong` `putFloat` … | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `AnnotationArrayBuilder` | 14 | `putString` `putBoolean` `putByte` `putChar` `putShort` `putInt` `putLong` `putFloat` … | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `FieldBuilder` | 14 | `compile` `rename` `makePrivate` `makePublic` `makeProtected` `makePackagePrivate` `toggleStatic` `toggleFinal` … | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `MethodBuilder` | 14 | `compile` `makePrivate` `makePublic` `makeProtected` `makePackagePrivate` `toggleStatic` `rename` `exceptions` … | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `FieldInitializerBuilder` | 13 | `setInt` `setLong` `setFloat` `setDouble` `setChar` `setString` `setBoolean` `setByte` … | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `ClassBuilder` | 11 | `addField` `addMethod` `addConstructor` `addClinit` `addAnnotation` `finishBuildAndFreeze` `methodWrappers` `ctClass` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `Websocket` | 8 | `connect` `getWs` `sendText` `onConnect` `onTextMessage` `onDisconnect` `onError` `onFrame` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `FReflection` | 8 | `createClassBuilder` `getClassFromClassBuilderResult` `createLibraryBuilder` `createLibrary` `compileJavaClass` `getCompiledJavaClass` `getAllCompiledJavaClassVersions` `loadJarFile` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `ProxyBuilder` | 7 | `addMethod` `buildInstance` `factory` `proxiedMethods` `proxiedMethodDefaults` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `ConstructorBuilder` | 5 | `body` `guestBody` `buildBody` `endAbstract` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `ScriptEventListener` | 4 | `triggerInline` `getCreatorName` `getWrapper` `getCtx` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `BodyBuilder` | 3 | `appendJavaCode` `appendGuestCode` `finish` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `FileLineIterator` | 3 | `hasNext` `next` `close` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `HTTPRequest` | 3 | `conn` `connectTimeout` `readTimeout` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `Response` | 2 | `byteArray` `responseCode` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `LibraryBuilder` | 2 | `addConstructor` `finishBuildAndFreeze` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `CombinedVariableClassLoader` | 2 | `addClassLoader` `hasJar` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `FileHandler` | 1 | `streamBytes` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |
| `ProxyReference` | 1 | `parent` | 运行时 Java 编译 / 动态类生成 / Jar 加载 |

### 5.3 内部实现 / 上游移除 / 工具排除

- 内部实现 252 类（GUI 61、mixin 64、access 25、config 11、client 内部 12、core 内部 45、wagyourgui/根工具等）：无脚本可见面，全部 `not-applicable`。
- 上游已移除 21 类、工具/doclet 排除 15 类：不参与对比，见 14 号附录 / `_missing.json` / `_excluded.json`。

## 6. 并行与冲突矩阵

### 6.1 推荐并行波次

| 波次 | 可并行任务 | 说明 |
| --- | --- | --- |
| W0 | `G-004` ∥ `G-001` ∥ `G-002` ∥ `G-003` | compat 回桥与三条设计 spike 互不冲突 |
| W1 | `G-012` ∥ `G-013` ∥ `G-017` ∥ `G-017B` ∥ `G-019` ∥ `G-010` ∥ `G-014` | 实体/事件/数据/容器各一条竖切 |
| W2 | `G-020` ∥ `G-022` ∥ `G-024` ∥ `G-024B` ∥ `G-025` ∥ `G-026` ∥ `G-027` ∥ `G-028` ∥ `G-018` ∥ `G-018B` | 按模块/文件隔离；同文件任务串行 |
| W3 | 设计确认后：`G-031` ∥ `G-032` 族 ∥ `G-033` 族 ∥ `G-035/036` 族 | 渲染/GUI/包级；先做基类再组件 |

### 6.2 共享文件（必须串行或拆分）

| 文件/模块 | 相关任务 | 规则 |
| --- | --- | --- |
| `core/api/player/PlayerApi.java` + `ports/PlayerPort.java` + `FabricPlayerPort.java` | G-019/020/021/028/035/037 | 同时只能一个 owner；容器任务按顺序合并 |
| `core/api/client/ClientApi.java` + `ports/ClientPort.java` + `FabricClientPort.java` | G-018B/024/024B/024C/025 | Options/Registry 串行合并 |
| `core/api/CommandApi.java` + `CommandArg.java` + `CommandSpec.java` + `FabricCommandPort.java` | G-026/026B | 命令任务串行 |
| `core/api/input/InputApi.java` + `ports/InputPort.java` | G-028/028B | 输入任务串行 |
| `core/api/types/BlockState.java` + `Block.java` + `Chunk.java` | G-017/029 | 数据类任务串行 |
| `core/api/render/components/*` | G-032/032B/033/033B/034/034B/034C/035/036/036B | 基类先合并，再组件；禁止并行改同一组件包 |
| `core/api/entity/EntityApi.java` + `SpeciesEntities.java` + `EntityPort.java` + `FabricEntityPort.java` | G-012/022/023 | 实体基类先合并，物种任务再各自加类 |
| `core/api/world/WorldApi.java` + `WorldPort.java` + `FabricWorldPort.java` | G-010/039 | G-039 在 compat 侧，不改 WorldApi |
| `core/api/HudApi.java` + `RenderApi.java` + `FabricHudRegistry.java` | G-001/030/032/032B/033/033B/034/034B/034C/035/036/036B/038 | 先 G-001 定模型，再按基类→组件顺序 |
| `core/api/types/ItemStack.java` + `ItemStackBuilder.java` | G-017/027/027B | ItemStack 任务串行 |
| `core/event/def/**` | G-013/014/015/016/031 | 按 domain 目录隔离，可并行；ProductionEventSet 单点修改需协调 |
| `product/adapter/PortEventWiring.java` | G-014/015/016 | 单点文件，按任务顺序合并 |
| `jsmacros-compat/src/**` | G-004/005/039 | 无 git；同一时间只允许一个 owner |

## 7. DoD 与验证命令

每个任务完成必须同时满足：

1. **竖切完整**：port + facade + adapter + StubAdapter（如涉及）；事件进 `ProductionEventSet`。
2. **注解完整**：`@ApiSince` / `@ApiMC` / `@ApiLoader` / `@ApiReadOnly` / `@ApiOptional` / `@ApiPermission` / `@ApiType` 按成员语义标注。
3. **测试**：core 单测 + e2e 用例；动作类至少一个脚本级用例；mixin/版本差异有对应节点。
4. **文档**：javadoc + `@zh`；`NeoMacros-Docs` 生成 d.ts/类页/事件页；d.ts 无 `unknown` 悬空引用。
5. **构建**：`./gradlew build`；`./gradlew :core:test :e2e:test`；`bash scripts/smoke-build.sh`；四节点（1.21.6/26.1.2/26.2/26.3）编译绿。
6. **CI**：推送后查 check-runs 全绿（不要只看 run 页面标题）。
7. **报告**：`plan/reports/G-xxx.md` 写清改动文件、成员映射、D/E 结论、测试命令与结果、遗留。

compat 任务（G-004/005/039）额外：`npm test` 20/20；`npm run audit` 完备性门禁绿；`docs/missing-apis.md` 重新生成。

## 8. 风险与对策

| 风险 | 对策 |
| --- | --- |
| 成员被误判为 B/C，实际是服务端-only | 先查 13 号 §2.3；拿不到客户端值就登记 D，不挖无意义 accessor |
| 26.x 私有 getter/字段不可达 | 先做 accessor 可行性验证；不可达则 D；不要为了一个成员引入大范围 mixin |
| 渲染/GUI/包级任务过大 | G-001/002/003 先定模型；组件任务按基类→子类分波，单任务不超过 ~250 成员 |
| 共享文件冲突 | 按 §6.2 串行；任务卡只列本任务文件；合并前 rebase |
| 基线漂移 | 任务报告记录 NeoMacros HEAD；若 HEAD 变化，先跑 `gen-development-plan.mjs` 重生成任务索引 |
| compat 无 git、易并发冲突 | 同一时间只允许一个 compat owner；改前备份/对比；audit 后立即生成 missing-apis |

## 9. 追溯与重生成

- 任务索引：`JsMacros-Dump/gap/tasks.json`；每个任务的 `members[]` 是 gap.json 成员的子集，含 `fqn/name/signature/status/target/reason`。
- 逐成员任务明细：`JsMacros-Dump/gap/tasks-detail.md`（人读版，按任务/排除项列出全部成员）。
- 完整性校验：`node tools/gap/verify-plan.mjs`；当前 2,750 个活跃成员全部恰好分配一次（0 未分配、0 重复）。
- 15 号的任务表/成员索引由 `tools/gap/gen-development-plan.mjs` 生成；要改任务范围/依赖/验收，改该脚本的 `TASKS` 表后重跑，不要手改本册任务卡。
- 重新生成：

```bash
cd C:/Projects/fabric-mod/JsMacros-Dump
node tools/gap/gen-development-plan.mjs
node tools/gap/render-development-plan.mjs
```

- 如果发现 `G-TRIAGE` 不为 0，先在本册/13 号做 triage，不要直接开工。
- 本册与 14 号、`tasks.json` 必须同批更新；任务号 `G-xxx` 一旦被认领不要复用。

