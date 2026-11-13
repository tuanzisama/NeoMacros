# 本波设计拍板点汇总（G-001/G-002/G-003 + 实施期发现）

> 汇总自 `G-001.md` §11、`G-002.md` §14、`G-003.md` §13 与 G-012/G-013 实施报告。
> 状态：全部 **待需求方裁定**。裁定后由协调者回写 12/13/15 号并解锁对应下游任务。
> 日期：2026-11-14。

## A. G-001 → 解锁 G-030/032/032B/033/034 族（8 点，①②⑤⑥为先决）

| # | 拍板点 | 推荐 |
|---|---|---|
| 1 | 组件类命名：`Text/Image/Item` 与挂根类冲突 | `Draw2DText/Draw2DRect/Draw2DLine/Draw2DImage/Draw2DItem`（备选 `HudText/…`） |
| 2 | `Builder` 类族折叠进元素句柄（fluent setter）vs 按 15 号任务卡另建 | 折叠（否决需改 gen-development-plan.mjs 重生成任务卡） |
| 3 | 层/元素 id 按模块 owner 隔离 + 卸载自动 clear（owner 缺失回退裸键） | 采用 |
| 4 | 颜色参数 `number \| string` 双形（12 号 §4.15 字符串 vs 现状 ARGB int） | 双形 |
| 5 | 删除 `hud.textIds()` / `render.layerIds()`，由 `hud.layers` / `render.layers` 属性取代 | 删除（compat 未使用） |
| 6 | **纹理层建/不建**（G-030 缩水开关） | 建（`hud.texture` 最小注册表） |
| 7 | 兼容糖（`hud.addText` 等 `$legacy` 层）长期去留 | 保留（compat 冻结契约） |
| 8 | 层 z 是否与原版 HUD 元素交错 | v1 不交错 |

另有与 12 号 §4.15 的两处字面出入（`ids()` → `ids` 属性化；"重塑进 layer 模型"读作糖化保留），回写清单见 G-001.md §12。

## B. G-002 → 解锁 G-031（4 点，①为硬前置）

| # | 拍板点 | 推荐 |
|---|---|---|
| 1 | **send 信任门**（任务卡明列）：甲=新增能力键 `packet`（buffer.send 字节上 wire 走 `packet`，`client.sendPacket(JavaObject)` 维持 `java` 口径）／乙=并入 `java`／丙=免门控 | 甲 |
| 2 | `packet:receive` 可取消性：维持 12 号拍板不可取消 vs 升级可取消+保护名单 | 维持不可取消 |
| 3 | L2 默认抑制名单内容（delimiter/实体移动族/玩家移动，可配 `CoreOptions.packetIgnore`） | 采用报告 §3 推荐名单 |
| 4 | `client.receivePacket` / helper `receivePacket`（伪造下行注入）判 D | D 定案 |

落点偏离备忘：G-031 实施时两钩子落新 `ConnectionMixin`（非任务卡的 ClientPacketListenerMixin），需重跑 gen-development-plan.mjs 更新 targets。

## C. G-003 → 解锁 G-035/036/036B（5 点）

| # | 拍板点 | 推荐 |
|---|---|---|
| 1 | 入口命名 `player.buildScreen` vs 13 号字面 `screen.build` | `player.buildScreen`（第六轮 screen 已并入 player） |
| 2 | 同步语义豁免：`buildScreen/close/add` 同步 vs 12 号 §2.6 规则 3 Promise | 豁免（`close()` 既有先例） |
| 3 | `player.screen` 保持只读 vs 脚本屏返回 `BuiltScreen` 实例（改 PlayerApi 分发） | 保持只读 |
| 4 | 回调同步主线程派发+看门狗+truthy=消费 vs 异步 | 同步+消费 |
| 5 | 类名组：`BuiltScreen`、Mojang 控件词 `Button/Checkbox/Slider/EditBox/CycleButton`、宿主 `ScriptScreen` 留用+现查看器更名 `ScriptViewerScreen` | 采用 |

## D. 实施期新增拍板点

| 来源 | 拍板点 | 推荐 |
|---|---|---|
| G-013 遗留 | 字段级 `@ApiSince` 无注解目标（`@Target` 不含 record component），暂沿用类级 | 扩 `@ApiSince` 的 `@Target` 支持 RECORD_COMPONENT，或维持类级 |
| G-012 报告 §4.5 | 12 号 §2.3 `LivingEntity.armor[4]` → 四独立属性（与已发布 `WolfEntity.getArmor(): String` 返回类型冲突） | 四独立属性（headArmor/chestArmor/legArmor/footArmor 已落地），回写 12 号 |
| G-012 报告 §6.2 | 13 号 §5.10 `defaultMaxHealth` 与 JsMacros 26.x `getMaxHealth()` 语义分歧 | 降级为 A（一行回写） |
| G-010 遗留 | `playSoundFile` 的 JsMacros `Clip` 停止句柄不映射（能力缩减 L1）；权限门建议 L3 | 维持缩减，权限门随 L3 |
| G-019 遗留 | `getCraftableRecipes`/`getRecipes(boolean)` 需 Recipe 句柄类型 + 配方书 tab 访问器 | 转 G-020 承接 |

## E. 已顺带裁定（实施 agent 依既有拍板执行，列出备查）

- G-017：`BlockState.getBlock`/`getUniversal`/`Chunk.forEach`/`HitResult.resolve`/`Block.isMissed` 判 D（证据在 G-017.md）
- G-017B：**Plane3D 不建类，13 成员全 D**（五条证据，若异议需回 13 号重新拍板）
- G-013：`getInventory`×5 判 D（冻结载荷禁活句柄）、`EventSlotUpdate.type` 判 D（BaseEvent 保留键）
