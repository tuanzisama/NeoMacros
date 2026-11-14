# DOCS-WRITEBACK-15 · 15 号任务索引重生成与 targets 回写收尾清单

- 日期: 2026-11-14
- 触发: 43 个实现任务全部合入 neomacros main（`80ec5dc`）；按 15 号 §9「追溯与重生成」协议，改 `gen-development-plan.mjs` 的 `TASKS` 表后重跑生成链，**未手改 15 号正文**。
- 生成链: `node tools/gap/gen-development-plan.mjs` → `node tools/gap/render-development-plan.mjs` → `node tools/gap/render-tasks-detail.mjs` → `node tools/gap/verify-plan.mjs`（均在 `C:/Projects/fabric-mod/JsMacros-Dump` 下执行）。
- 基线: 15 号头部 NeoMacros HEAD 按 §8 协议更新为 `80ec5dc8`（完整 `80ec5dc8f32d3faef984a616f5c49de980646602`，2026-11-14 18:03 +0800）。

## 1. targets 修正逐条（14 个任务，均以报告证据为准）

| 任务 | 原 targets（节选） | 修正后 | 证据 |
| --- | --- | --- | --- |
| G-001 | …`product/adapter/FabricHudRegistry.java` | 删除该项 | G-032 偏离⑤：FabricHudRegistry 已删除（HudRegistry 接口随 HudApi 重写消失） |
| G-003 | 无 Screen 面 | +`ports/ScreenPort.java`、`core/api/render/screen/*` | G-003 §13 回写③「15 号 G-003/G-035 落点补 ScreenPort/render-screen」 |
| G-016 | `core/event/def/world/*.java`、`product/adapter/*Hub.java` | →`core/event/def/lifecycle/*.java`，+`core/event/PortEventWiring.java`、`ports/LifecyclePort.java`、`product/adapter/FabricLifecyclePort.java`（entity/ProductionEventSet 保留） | G-016 §O：「相对任务卡落点 def/world/*.java 为有意偏离」——资源包事件实际落 `def/lifecycle/`（与 game:start 同包）、源接线走 FabricLifecyclePort（非 hub） |
| G-027B | `ports/model/EnchantmentData.java` | →`core/api/types/Enchantment.java` | G-027B 偏离①：「新脚本类型 Enchantment（任务卡落点外的竖切必需）；落点文件 EnchantmentData.java 因此零改动」 |
| G-030 | `core/api/types/Image.java`、`core/api/HudApi.java`、`product/adapter/FabricHudRegistry.java` | →`core/api/types/CustomImage.java`、`ports/RenderPort.java`、`product/adapter/FabricRenderPort.java` | G-030 偏离⑤：三文件全零改动；纹理注册表归口 RenderPort（G-001 §5.1）；交付表实落 CustomImage/RenderPort/FabricRenderPort |
| G-031 | `product/adapter/mixin/ClientPacketListenerMixin.java` | →`product/adapter/mixin/ConnectionMixin.java` | G-002 §1/§13 落点偏离声明 + G-031「ConnectionMixin 落点声明」：收发两钩子落新文件 ConnectionMixin；ClientPacketListenerMixin 维持现状 |
| G-032 | …`product/adapter/FabricHudRegistry.java` | 删除该项，+`ports/RenderPort.java`、`product/adapter/FabricRenderPort.java` | G-032 偏离⑤（删除）+ 交付表（setLayerZ / 单常驻 HudElement 改造） |
| G-032B | …`product/adapter/FabricHudRegistry.java` | 删除该项，+`core/api/render/components/ElementSpec.java` | G-032B 裁定「任务卡落点 FabricHudRegistry 已由 G-032 删除」+ 交付表（ElementSpec 迁入 components） |
| G-034 | `core/api/HudApi.java`、`product/adapter/FabricHudRegistry.java` | →`core/api/render/HudLayer.java`、`ports/RenderPort.java`、`ports/model/DrawContext2d.java`（components/* 保留） | G-034 落点段「HudApi 本批零改动…落点文件 = HudLayer.java」+ 偏离表「ports 任务卡落点未列但为本批面」；FabricHudRegistry 已删 |
| G-034B | `core/api/HudApi.java`、`product/adapter/FabricHudRegistry.java` | →`core/api/render/HudLayer.java`（components/* 保留） | G-034B 回写③「落点删 FabricHudRegistry/HudApi」+ 落点段「HudApi 零改动，36 成员落 HudLayer」 |
| G-035 | PlayerApi/ScriptScreen/NeoMacrosFabric | +`ports/ScreenPort.java`、`core/api/render/screen/*`、`product/adapter/FabricScreenPort.java`、`product/adapter/mixin/CheckboxAccessor.java`、`e2e/harness/*` | G-003 §10/§13 回写③ + G-035「任务卡外文件…落点偏离需回写 15 号」清单（设计 §9 竖切落点表明列项） |
| G-036 | `product/fabric/ScriptScreen.java`（零改动） | 删 ScriptScreen，→`ports/ScreenPort.java`、`product/adapter/FabricScreenPort.java`、`product/fabric/NeoMacrosFabric.java`、`e2e/harness/*` | G-036 泳道「ScriptScreen 零改动」+ 遗留4「15 号落点回写：落点 = 任务卡 render/screen/* + 注册点，另披露 ports/ScreenPort、e2e harness」 |
| G-036B | 同上 | 删 ScriptScreen，同上 +`product/adapter/mixin/CycleButtonAccessor.java` | G-036B 泳道「ScriptScreen 零改动」+ 遗留3 同口径（另披露 mixin） |
| G-038 | `core/api/types/Image.java`、`product/adapter/FabricHudRegistry.java` | →`core/api/types/Texture.java`、`ports/RenderPort.java`、`product/adapter/FabricRenderPort.java`（HudApi 保留） | G-038 偏离①「三文件只动了 HudApi；新增落点 types/Texture.java、RenderPort、FabricRenderPort」 |

说明：
- 全部新路径已对 neomacros 工作树逐一核验存在；`FabricHudRegistry.java` 确认已删除（7 个任务的引用全部清除，gen 脚本内 0 残留）。
- 追加型披露（卡上文件本身正确、另有任务卡外连带，如 G-017 的 WorldPort/FabricWorldPort、G-012 的 EntityGlowMixin、G-018/G-019/G-021/G-024 的 1 行连带、注册点 NeoMacrosFabric/E2ERuntime/ApiCompletenessSurface）按 15 号 §1.2「任务卡没列但必然触碰的注册点/连带由报告披露」惯例**未加入** targets；仅「卡上文件错/未用」的替换型偏离（上表）做了修正。

## 2. 范围类 / 说明修正（拍板②，G-001 §12 回写4）

- G-032B、G-034、G-034B：任务卡「范围类」剔除 `Builder`（TASKS 表原本已无显式 Builder，但成员并集会把 gap 的 `*Builder` 嵌套类并回展示——gen 新增 `SCOPE_CLASS_DROP` 过滤器，仅影响范围类展示，**不改成员分配**）。
- G-034C：范围类剔除 `RenderElementBuilder`（同上过滤）；notes 增补范围说明「RenderElement.render 判 D、RenderElementBuilder 不建类（G-001 §12 回写4 / G-034C 回写③）」。
- G-001 §12 回写4 对 G-032B/G-034/G-034B 的「范围类删 Builder」字面要求在 TASKS 表已满足（表内本就无 Builder），实际展示层由上述过滤器闭环。

## 3. status 字段处理方式

- 先读脚本确认：gen 对 status 是自由字符串透传；`render-development-plan.mjs` / `render-tasks-detail.mjs` 各有一张 `STATUS` 映射表，未识别的枚举会原样打印英文；`verify-plan.mjs` 只特判 `blocked`/`design-first`/`no-op` 三条结构规则。
- 结论：**扩脚本支持（改动最小化）**——两张 STATUS 表各加两行：`done: '已完成'`、`deferred: '延后'`。
- 落库结果（tasks.json 共 47 任务）：
  - **43 个已完成任务 → `done`**（G-001/002/003 设计 spike + G-010~G-029 + G-030~G-038 全部实现任务；43 份 G 报告「状态: done」逐一核对）；
  - **G-004/G-005/G-039 → `deferred`（渲染「延后」）**——compat 侧三任务未实施，按任务书「deferred」口径标延后；
  - **G-040 维持 `no-op`（不实施）**。
- 渲染核验：15 号正文 `已完成`×86（43 任务 × 总览表+任务卡两处）、`延后`×6；`可认领/阻塞/设计先行` 归零；tasks-detail.md 同步 `已完成`×43、`延后`×3。
- meta.note 同步改为「G-004/G-005/G-039 在 jsmacros-compat（延后）；G-040 为不实施；EXCL-* 为不开发项。」

## 4. 基线哈希更新（§8 协议）

- 15 号头部：`× NeoMacros 80ec5dc8（2026-11-14 18:03）`（原 `5ffcaf43…2026-11-11 20:33`）。
- 链路处理：头部哈希源为 `gap.json#meta.neomacrosCommit`（build-gap 读 `gap/mapping.json`），故**同步更新两文件的该字段**为 `80ec5dc8f32d3faef984a616f5c49de980646602`，并把 render 脚本中硬编码日期改为 2026-11-14 18:03；`jsmacrosCommit`（JsMacros-CE `83089a69`）不变。
- **未重跑 build-gap**：仅改 meta 字段，审计数据（gap.json 类/成员/summary）零变动，避免重建链引入成员分配漂移；mapping.json 与 gap.json 同步改，未来 build-gap 重跑保持一致。

## 5. 生成命令输出摘要

```text
$ node tools/gap/gen-development-plan.mjs
summary: { tasks: 47, assignedMembers: …, activeTotal: 2750,
           exclusions: { EXCL-INTERNAL: 0, EXCL-D: 241, EXCL-E: 129, G-TRIAGE: 0 },
           unassigned: 0, compatStale: 216, compatNew: 39 }
tasks: 43 × done（G-001…G-038）、3 × deferred（G-004/G-005/G-039）、1 × no-op（G-040）

$ node tools/gap/render-development-plan.mjs
written 15-jsmacros-gap-development-plan.md

$ node tools/gap/render-tasks-detail.mjs
written gap/tasks-detail.md
```

## 6. verify-plan 输出（逐字）

```text
$ node tools/gap/verify-plan.mjs
active members: 2750
assigned/excluded: 2777
tasks: 47
exclusions: {"EXCL-INTERNAL":0,"EXCL-D":241,"EXCL-E":129,"G-TRIAGE":0}
compat stale: 216; CE new: 39
OK: every active member is assigned exactly once
```

G-TRIAGE = 0，2,750 活跃成员全部恰好分配一次。

## 7. 改动文件与备份 / 回滚

改动（就地更新，均无 git 的 JsMacros-Dump 侧 7 个文件 + neomacros-docsx 1 个生成物）：

1. `C:/Projects/fabric-mod/JsMacros-Dump/tools/gap/gen-development-plan.mjs`（TASKS 表 targets/status/范围类/notes + SCOPE_CLASS_DROP + meta.note）
2. `C:/Projects/fabric-mod/JsMacros-Dump/tools/gap/render-development-plan.mjs`（STATUS +done/deferred；头部日期）
3. `C:/Projects/fabric-mod/JsMacros-Dump/tools/gap/render-tasks-detail.mjs`（STATUS +done/deferred）
4. `C:/Projects/fabric-mod/JsMacros-Dump/gap/tasks.json`（生成物）
5. `C:/Projects/fabric-mod/JsMacros-Dump/gap/tasks-detail.md`（生成物）
6. `C:/Projects/fabric-mod/JsMacros-Dump/gap/gap.json`（仅 meta.neomacrosCommit）
7. `C:/Projects/fabric-mod/JsMacros-Dump/gap/mapping.json`（仅 neomacrosCommit）
8. `C:/Projects/fabric-mod/neomacros-docsx/15-jsmacros-gap-development-plan.md`（生成物）

备份（改前快照）：`C:/Projects/fabric-mod/neomacros/plan/reports/docsx-backup-20261114/`
含 gen/render/render-tasks-detail 三脚本、tasks.json、15 号三件套（任务书要求）+ gap.json、mapping.json（本次连带改动，一并备份），另含该目录此前已有的 12/13 号快照。
回滚锚提交：neomacros `d18af43`（`chore(plan): backup gap plan sources before 15号 §9 regeneration`，`git add -f` 入库）。回滚 = 用该目录覆盖三处生成物/脚本后重跑生成链。

## 8. 未处理项及原因

1. **G-002 卡片 targets 仍列 `ClientPacketListenerMixin.java`**：G-002 §1/§13 的落点偏离声明把回写目标明确限定为 `gap/tasks.json#G-031` targets；且 G-002 设计仍分析 ClientPacketListenerMixin 的 sendChat 与 packet:send 次序（§2.4），该文件在其设计范围内，故未动。
2. **G-001 §12 回写5（G-030 depends: G-001 → G-038）未执行**：属设计期建议；实际波次为 G-030 先行（基线 104b5bc）→ G-038 消费其交棒（3f9c00c→80ec5dc），现在改 depends 会颠倒已发生的执行顺序；无任何报告确认采纳该建议。
3. **15 号 §6.2 共享文件表仍有一行含 `FabricHudRegistry.java`（render 脚本静态模板文本，非 TASKS 数据）**：报告未要求回写该表；为 render 脚本改动最小化未动。后续如需，可把该行改为 `HudApi + RenderApi + RenderPort + HudLayer`。
4. **审计侧回写未执行**：`gap/mapping.json` 成员 target 回填（Draw2DElement/Draw2DImage/Draw2DItem/Alignable/RenderElement 等）、`build-gap.mjs` MEMBER_ALIASES 补录（ovText→overlayText 等）、成员级 D→B 状态修正（如 G-026B 遗留4 的 9 个 suggest*）——属 gap 审计重建链（seed-mapping/build-gap），不在本任务「任务索引重生成」范围；重跑 build-gap 还可能引入成员分配漂移、危及 verify-plan OK。
5. **12/13 号 docsx 回写未执行**（G-001 §12 items1-3、G-002 §13 items1-5、G-003 §13 items1-2/4-5、G-010/G-016 等的 12/13 号行）：本任务只重生成 15 号/任务索引，docsx 12/13 号归文档车道。
6. **G-033/G-033B/G-022 等「卡上列了但零改动」的落点未删**：G-033B 偏离⑧自判「按 G-033 既成结构解释，非范围偏离」、Draw3DPort「任务卡落点预留」，G-022 同口径；均无回写请求，保留为泳道预留落点。
7. **15 号静态使用指引（§0 读法「先做 G-001/002/003…再开 G-031~G-038」、§6.1 波次表）为 render 模板文本**：非任务数据，未随 status 变更改写（属模板措辞，不在 §9 协议的「任务范围/依赖/验收」之列）。
8. **`plan/TASKS.md` 及其他 neomacros 文件未动**（任务禁令）；JsMacros-Dump 无 git，未做提交，回滚以上述备份目录为准。
