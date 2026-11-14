# 12/13 号缺口波回写清单（2026-11-14 第八轮）

- 执行范围：`neomacros-docsx/12-api-catalog.md`、`neomacros-docsx/13-jsmacros-gap-api.md` 就地修改（先备份）
- 素材：`plan/reports/WAVE-DECISIONS.md`（A/B/C/D/E 组，需求方 2026-11-14 确认采纳推荐）+ 43 份 `plan/reports/G-*.md` 回写清单（36 份含"回写"字样）
- 原则：每条忠实转录报告原文证据，不新增判断；拿不准/超范围条目跳过并列入本文"未回写"节
- 基线：main `80ec5dc`；两文档原 749 / 489 行 → 现 852 / 512 行

## 备份路径（回滚锚，git add -f 入库）

- `plan/reports/docsx-backup-20261114/12-api-catalog.md`（原样 749 行）
- `plan/reports/docsx-backup-20261114/13-jsmacros-gap-api.md`（原样 489 行）

## 一、12 号回写（30 条）

| # | 回写项 | 落点章节/行 | 来源报告 |
|---|---|---|---|
| 1 | 头部追加"2026-11-14 第八轮修订（缺口波回写）"摘要行（A/B/C/D/E 五组 + 事件目录回写总述） | 头部（状态标注行前） | WAVE-DECISIONS 全文 |
| 2 | 类树增补 `HudLayer · RenderLayer · Draw2DElement · Draw2DText/Rect/Line/Image/Item`（挂 `neo` 根、wraps 留空） | §2.1 类树代码块 | G-001 §12 回写 2 |
| 3 | `air/maxAir/glowing` 自 LivingEntity 上移 `Entity` 基类（MC 声明在 Entity、gap 成员在 EntityHelper，脚本侧属性名不变） | §2.3 `Entity` 行 | G-012 §6.4（WAVE D 组） |
| 4 | `armor*: ItemStack[4]` → `headArmor/chestArmor/legArmor/footArmor` 四独立属性（与已发布 `WolfEntity.getArmor(): String` 返回类型冲突） | §2.3 `LivingEntity` 行 | G-012 §4.5/§6.3（WAVE D 组） |
| 5 | 补 `containerId*`（`getCurrentSyncId` 更名，Mojang `AbstractContainerMenu.containerId` 优先） | §2.4 `Inventory` 行 | G-019 偏离与回写 |
| 6 | 补 `open(): Promise<void>`（`openGui` 更名，与 `close()` 对称） | §2.4 `Inventory` 行 | G-019 偏离与回写 |
| 7 | 屏幕特化树枚举补 `LoomInventory`/`CreativeInventory` 两行 | §2.4 `Inventory` 行（特化树括注；来源写"§4.6 类树"，按表格主题落 §2.4 枚举） | G-021 遗留 1 |
| 8 | 规则 3 增补 buildScreen 同步豁免（`buildScreen/close/add/remove/setControl/reload` 同步，`Screen.close()` 先例，开屏失败同步 throw） | §2.6 形态规则 3 | WAVE C 组拍板② / G-003 §13-2 |
| 9 | `neo.client` 权限注改"`buffer.send` 需 `packet`；`client.sendPacket(JavaObject)` 维持 `java` 口径" | §4.0 总览 `neo.client` 行 | WAVE B 组拍板①甲 / G-002 §13-2 |
| 10 | **§4.15 整节重写**（G-001 §4.1~§4.3 签名表整体替换现表格）：A 组 8 点拍板块（①Draw2D* 命名②Builder 折叠③owner 隔离④颜色双形⑤删 ids()⑥建纹理层⑦糖保留⑧z 不交错）+ 模块成员表（hud.layer/layers/removeLayer/addText/texture/textures/render.*）+ `HudLayer` 接口 + 公共 options 双形 + `Draw2DElement`/组件类 + `RenderLayer` + 每帧重投五条（G-001 §2.2 原文）+ 两处字面出入（`ids()` 属性化、"重塑=糖化保留"）+ 不做的成员 D/延后表 + 免门控/不新增事件注 | §4.15 全节 | WAVE A 组 8 点 + G-001 §12 回写 1（+ G-032/032B/033/034/034B/034C/038 实施状态引注） |
| 11 | `player.screen` 保持只读拍板注（脚本屏回中立基类快照、跨脚本隔离） | §4.16 `player.screen` 行 | WAVE C 组拍板③ / G-035 拍板③落实 |
| 12 | `screen.build` 行 → `player.buildScreen(spec): BuiltScreen`（同步+豁免+消费位 gui.on+看门狗+类名组 BuiltScreen/控件词/ScriptScreen 留用+ScriptViewerScreen+getOnClose/getAction compat 自持+微调默认值） | §4.16 `screen.build` 行（整行替换） | WAVE C 组拍板①②④⑤ / G-003 §13 回写 1 / G-035 拍板对应表+偏离① |
| 13 | 新增 `packet` 权限行（覆盖 `buffer.send`；README 决策登记随文档批；G-031 handle 权限门缺口注） | §4.19 权限目录表 | WAVE B 组拍板①甲 / G-002 §13-2 / G-031 遗留 1 |
| 14 | 字段级 `@ApiSince` 拍板记录（`@Target` 不含 record component 暂沿用类级；拍板=扩 RECORD_COMPONENT 或维持类级 + 实施现状注） | §5.1 语义规则新增 bullet | WAVE D 组（G-013 遗留） |
| 15 | `entity:damage` payload 补 `health`（受伤后生命值） | §5.2 行 | G-014 #33 |
| 16 | `entity:heal` payload 补 `health`（恢复后生命值） | §5.2 行 | G-014 #35 |
| 17 | `player:death` 补 `deathPos: BlockPos`、`inventory: ItemStackData[]` | §5.2 `player:spawn/respawn/death` 行 | G-014 #8/#9 |
| 18 | `player:health` 补 `change`（带符号差量） | §5.2 行 | G-014 #17 |
| 19 | `player:effect` 补 `previousEffect?: EffectSnapshot\|undefined` | §5.2 行 | G-014 #30 |
| 20 | `player:exp` 补 `prevLevel/prevTotal/prevProgress` + 触发条件扩为"等级或总经验值变化" | §5.3 行 | G-014 #10-13 + 行为变更 2 |
| 21 | `player:heldItem` 补 `offHand` + `previous` 真正接通（边沿不再打断差量链） | §5.3 行 | G-014 #18/#20 + 行为变更 3 |
| 22 | `player:armor` 补 `slot/item/oldItem` + 按变化槽位逐个发布（首次采样只建基线） | §5.3 行 | G-014 #1-3 + 行为变更 1 |
| 23 | `player:riding` 补 `previousVehicle`（下车侧） | §5.3 行 | G-014 #29 |
| 24 | `game:resourcePack` payload `id?` → `isGameStart, loadedPacks: string[]`（草稿逐字） | §5.3 行 | G-016 目录行草稿 + L1 |
| 25 | 新增 `entity:customName` 行（`uuid, oldName?, newName?`，草稿逐字） | §5.3 新行 | G-016 目录行草稿 |
| 26 | `packet:receive/send` payload 细化 `{id: string, flow, protocol, buffer}` + 禁用 `type`/`direction` + protocol 取 **`handshake`**（修正 `handshaking` 笔误）+ receive 维持不可取消 + send `mutable={"buffer"}` + buffer 生命周期引用 G-002 §4.4 | §5.3 packet 行 | G-002 §2.2/§2.3 + §13-1 / G-031 §遗留5 / WAVE B 组拍板② |
| 27 | 通配忽略白名单与 `EventBus.DEFAULT_WILDCARD_IGNORES` 对齐（删文档漂移项 `world:chunkLoad/Unload`/`entity:load/unload`/`entity:spawn`，补代码实有 `sound:*`/`entity:metadata`/`entity:customName`；真源改标 EventBus） | §5.4 段 | G-002 §13-3 + G-016 L5 |
| 28 | L2 发布前抑制名单段（G-002 §3 推荐名单 9 项 + `CoreOptions.packetIgnore` 显式覆盖 `[]`=全放行） | §5.4 新段 | WAVE B 组拍板③ / G-002 §3 |
| 29 | `ItemStackHelper/CreativeItemStackHelper` 写面 → `ItemStackBuilder`（原"inventory 模块动作（写）"口径失效，模块已裁撤） | §6.1 对照表行 | G-027B 回写 8 |
| 30 | 可取消集定案：`packet:receive` 维持不可取消，全目录 8（不升 9） | 附：现状映射核对 bullet | WAVE B 组拍板② |

## 二、13 号回写（64 条）

### §2.2 不映射（D）清单追加（4 条）

| # | 回写项 | 落点 | 来源报告 |
|---|---|---|---|
| 31 | `Plane3D` 不建类、13 成员全 D 行（五条证据逐条转录） | §2.2 新行 | G-017B Plane3D 结论（WAVE E 组确认） |
| 32 | Text 成员 D 行（`withCustomClickEvent/getCustomClickValue`、`getFormatting`、`getColorIndex/getColorValue/getCustomColor`、`getInsertion`，证据①-④） | §2.2 新行 | G-017B D 证据 ①-④ |
| 33 | `Vec3D` 段类型 D ×3 行（`Box.pos`/`Line3D.pos`/`Line3D.getPos()`） | §2.2 新行（来源写"§2.3"，按表格主题落 §2.2 不映射 D） | G-033 遗留 2 + G-033B 遗留 5 |
| 34 | packet D 八条证据行（raw `packet` 字段、`replacePacket`、`receivePacket`×2、协议内省 6 签、NBT、Date、PublicKey、BitSet、nullable×2 及各自理由） | §2.2 新行 | G-002 §6.3/§13-4 + WAVE B 组拍板④ + G-031 |

### §2.3 服务端-only 物种状态（7 条）

| # | 回写项 | 落点 | 来源报告 |
|---|---|---|---|
| 35 | `DisplayEntityHelper` D 理由更正（`GenericInterpolator 私有` → `InterpolationHandler` 四节点理论可达的出入；本批按任务卡明令标 D，复议改 B 落点已就绪） | §2.3 `DisplayEntityHelper` 行 | G-022 §6.7/L2 |
| 36 | 新增 D 行 `EnderDragonEntityHelper` 体节 3 签 | §2.3 新行 | G-023 §6 |
| 37 | 新增 D 行 `SpellcastingIllagerEntityHelper.getCastedSpell` | §2.3 新行 | G-023 §6 |
| 38 | 新增 D 行 `OcelotEntityHelper.isTrusting` | §2.3 新行 | G-023 §6 |
| 39 | 新增 D 行 `FishingBobberEntityHelper.hasCaughtFish` | §2.3 新行 | G-023 §6 |
| 40 | 新增 D 行 `DolphinEntityHelper.getTreasurePos` | §2.3 新行 | G-023 §6 |
| 41 | 新增 D 行 `BoatEntityHelper.isOnLand/isInAir` | §2.3 新行 | G-023 §6 |

### §3.2 物种映射表修正（11 条）

| # | 回写项 | 落点 | 来源报告 |
|---|---|---|---|
| 42 | ArmorStand `isVisible` → `isInvisible()` 取反落地 + 上提 `Entity` 基类回写建议 | §3.2 ArmorStand 行 | G-022 §6.1 |
| 43 | Display `getVisibilityBoundingBox` → `visibilityBoundingBox*`（原草拟 `boundingBox*` 与碰撞盒冲突） | §3.2 Display 行 | G-022 §6.2 |
| 44 | Panda `isHiddenGeneRecessive/isMainGeneRecessive` → 两 no-arg 属性（替代 `isRecessive(gene)` 谓词） | §3.2 Panda 行 | G-022 §6.3 |
| 45 | Guardian `hasSpikesRetracted` → `moving*`（原 `isActive*` 草拟有误） | §3.2 Guardian 行 | G-023 §6 修正 1 |
| 46 | Drowned `hasNautilusShell` D 评估 → **A** | §3.2 Drowned 行 | G-023 §6 修正 2 |
| 47 | AbstractHorse `isAngry` → `standing*`（"仅 ZombieHorse/SkeletonHorse?"注不成立） | §3.2 AbstractHorse 行 | G-023 §6 修正 3 |
| 48 | Donkey `hasChest` 上提 `AbstractChestedHorseEntity`（驴/骡/羊驼同获，注册表补 mule） | §3.2 Donkey 行 | G-023 §6 修正 4 |
| 49 | Axolotl `getVariantId` → `variantId*`（`variant*` 被串名占用） | §3.2 Axolotl 行 | G-023 §6 修正 5 |
| 50 | TropicalFish `getVariant` → A（pattern 串）、`getVarietyId` → `varietyId*` | §3.2 TropicalFish 行 | G-023 §6 修正 6 |
| 51 | EndCrystal `isNatural` → `showingBottom*` | §3.2 EndCrystal 行 | G-023 §6 修正 7 |
| 52 | 表注补记：新增落地名清单、判别型 A 确认 + 3 条注册表 type id、`Animal.isFood` 两签名合并、A 行回桥面索引 | §3.2 表后注（新增引用块） | G-023 §6 修正 8-11 |

### §4 容器特化树修正（9 条）

| # | 回写项 | 落点 | 来源报告 |
|---|---|---|---|
| 53 | `RecipeInventory` recipe book 族状态 → **继续遗留**（需 `Recipe` 句柄 + 配方书 tab 访问器，转物品面批次/配方书专项承接） | §4 RecipeInventory 行 | G-019 遗留① → G-020 遗留①（WAVE D 组"转 G-020 承接"落定） |
| 54 | `FurnaceInventory` 改挂 `RecipeInventory` + 无参 `getInput()`→`getSmeltedItem()`（同名冲突取舍）+ `input` 让位 2D 输入格 + `getFuelValues`→`fuelValues*` 属性 | §4 FurnaceInventory 行 | G-020 偏离①⑥⑧ |
| 55 | `EnchantInventory.getEnchantmentHelpers` D → **B**（`enchantments*`）+ `costs` 返回类型 `int[]`→`List<Integer>` 连带修复 | §4 EnchantInventory 行 | G-020 偏离③② |
| 56 | `VillagerInventory.isLeveled` → `showProgressBar()`、`getMerchantRewardedExperience` → 既有 `futureTraderXp`、`canRefreshTrades` 按原文落地 | §4 VillagerInventory 行 | G-021 偏离⑤⑥ |
| 57 | `BrewingStandInventory.isBrewablePotion(stack)` 保留栈参（原 `isBrewable(id)?` 与实签名不符） | §4 BrewingStand 行 | G-020 偏离⑤ |
| 58 | `HorseInventory` 三谓词 `canBeSaddled/hasArmorSlot/hasChest` 保留 JsMacros 原名（否决 `isSaddleable*`/`canWearArmor*` 改名） | §4 HorseInventory 行 | G-021 偏离③ |
| 59 | `LoomInventory.selectPattern*` 双成员并存（`selectPattern(int)` + `selectPatternId(String)`，否决合并） | §4 LoomInventory 行 | G-021 偏离⑧ |
| 60 | `StoneCutterInventory.canCraft` → `hasInputItem()`（否决 `output !== undefined` 近似） | §4 StoneCutter 行 | G-020 偏离④ |
| 61 | `CreativeInventory`：`getShownItems`→`shownItems*`、`getTabNames/getTabTexts`→`tabNames*/tabTexts*`、`destroyAllItems` 按原版 46 槽循环 | §4 CreativeInventory 行 | G-021 偏离①②④ |

### §5 模块缺口映射表（15 条）

| # | 回写项 | 落点 | 来源报告 |
|---|---|---|---|
| 62 | `getRespawnPos` 落点 `player.self.respawnPos` → **`world.respawnPos`**（JsMacros 读 ClientLevel 世界级 + 任务卡落点 WorldApi） | §5.1 行 | G-010 §6.1/L2（WAVE D 组相关） |
| 63 | `playSoundFile` 不映射 `Clip` 停止句柄（能力缩减维持）+ 权限门随 L3 注 | §5.1 `playSound` 行 | G-010 L1/L3（WAVE D 组） |
| 64 | `Chunk.forEach(includeAir, cb)` 判 D（98k 高频回调反模式，等价 `world.findBlocks`/`scanBlocks`） | §5.1 Chunk 行 | G-017 D 证据③（WAVE E 组） |
| 65 | `InteractionManagerHelper` 行回写：入口 `LocalPlayer.interactions` 属性（12 号未拍板注）+ 21 脚本面 + **D 7 签逐条证据**（checkBase/autoUpdateBase/setGameMode/setTarget int 面×2/onUpdate/ensureInteracting） | §5.2 行（整行替换） | G-028 偏离与任务卡外改动 + 成员表 |
| 66 | 命令自动补全落定：`getArgumentAutocompleteOptions` ⚠ → **B 已落地**（`command.suggestions(part)` + `SuggestionsBuilder` + `CommandArg.suggests`）+ `command.names` + **9 个 `suggest*` D→B** | §5.3 chat/command 行 | G-026 遗留 3 / G-026B 遗留 4 + 判定表 |
| 67 | `sendPacket/receivePacket/createPacketByteBuffer` 行回写：`client.packetIds` B、`sendPacket(JavaObject)` 改形 java 门、**`receivePacket` 判 D 定案**、T-517 已实施 | §5.4 client 行 | G-002 §6.2/§13 + WAVE B 组拍板④ + G-031 |
| 68 | `createScreen/openScreen` 行 → `player.buildScreen(spec)` 已实施 + `BuiltScreen`/控件类 + `getOnClose`/`getAction` 等 9 签 compat 自持 + **消费位以 `gui.on` 为准**（spec 内联回调返回值丢弃、boolean `true` 判定） | §5.5 行（整行替换） | G-003 §13 回写回写②④⑤ / G-035 偏离①④+拍板表 / G-038 遗留 1 |
| 69 | 纹理行 → `hud.texture`/`hud.textures`/`Texture` 句柄 + `createTexture`×2 → `new neo.CustomImage(…)` 已实施 | §5.5 texture 行（整行替换） | G-001 §11⑥（拍板⑥）+ G-030 + G-038 |
| 70 | Draw2D 行 → 元素句柄模型 + 组件类名 `Draw2D*` + 对齐面/bounds 收敛 + **D 4 签**（parsePercentage/render/build/buildAndAdd）+ nested Draw2D P3 延后 + 各批已实施状态 | §5.5 Draw2D 行（整行替换） | G-001 §12 回写 3 + G-034C D 证据 + G-032B/G-034/G-034B |
| 71 | Draw3D 行 → **D 4 签**（`render`×3 + `init`）+ `Vec3D` D×3 索引 + 已实施状态 | §5.5 Draw3D 行（整行替换） | G-033B 遗留 + G-033 |
| 72 | `EntityHelper` 行补 `air* maxAir*` 归属（自 LivingEntity 上移） | §5.10 EntityHelper 行 | G-012 §6.4 |
| 73 | `LivingEntityHelper` 行：四护甲属性、`maxAir` 移出、`hasLineOfSight(target, simpleCast?)` 可选参落地名、`canTakeDamage`→`canBeSeenAsEnemy`、**`defaultMaxHealth` → A**（WAVE D 降级；G-012 曾落 B 的分歧注） | §5.10 LivingEntity 行 | G-012 L3/L4 + §6.2/§6.6（WAVE D 组） |
| 74 | `BlockStateHelper` 行：`getBlock` 判 D、`getUniversal` 判 D（各附证据）、**`hasComparatorOutput` 证据更正 → 已落地 B**（`hasAnalogOutputSignal()` 两节点 javap 实证）、`blocksMotion`→`blocksMovement` 语义名注 | §5.10 BlockState 行 | G-017 D 证据①② + 偏离 2（WAVE E 组） |
| 75 | `ItemStackHelper` 行：`creative/creativeTab` 与 destroy/place restrictions **D 评估 → B**、`possibleEnchantments` ⚠ → B、三处更名（getDestroySpeed/canFitInsideContainerItems/is* 归一）、`stack.enchantments` 元素类型 EnchantmentData→Enchantment 句柄注 | §5.10 ItemStack 行 | G-027 偏离①⑧ + G-027B 偏离⑦⑧ |
| 76 | `HitResultHelper` 行：`resolve` 工厂 D 证据补记 + `isMissed` 判 D | §5.10 HitResult 行 | G-017 D 证据④⑤（WAVE E 组） |

### §6 事件增补表（18 条）

| # | 回写项 | 落点 | 来源报告 |
|---|---|---|---|
| 77 | `RecvPacket/SendPacket` 行 → 已实施状态 + payload 细化 + receive 不可取消拍板 | §6.1 行 | G-002/G-031 + WAVE B 组拍板② |
| 78 | `SignEdit` 行 → `screen:signEdit`（`pos` 已实施于既有事件，原草拟 `sign:edit` 不再新增） | §6.1 行 | G-015 回写清单 |
| 79 | `NameChange` 行（草稿逐字：`entity:customName`（`uuid, oldName, newName`；LOCAL 不可取消、newName 只读）已实施） | §6.1 行 | G-016 目录行草稿 |
| 80 | `ResourcePackLoaded` 行（草稿逐字：`isGameStart, loadedPacks`；原 `id?` 作废） | §6.1 行 | G-016 目录行草稿 |
| 81 | `ArmorChange` 行 → `slot/item/oldItem` + 逐槽发布已实施 | §6.1 行 | G-014 |
| 82 | `world:chunkLoad/full*` ⚠ → **A 落定**（路径不变量） | §6.2 行 | G-015 回写清单（证据 §18） |
| 83 | `player:heal/source` → **D 落定**（拆自原合并行；`entity:heal/source` 维持待核对） | §6.2 行（拆行） | G-014 §D1 |
| 84 | `world:leave reason?/message` ⚠ → **D 落定**（端口无载荷，待 T-519 复核注） | §6.2 行 | G-015 §D8 |
| 85 | `player:health/change*` → **已实施 B** | §6.2 行 | G-014 #17 |
| 86 | `player:exp/previous` → **已实施 B**（prevLevel/prevTotal/prevProgress + total） | §6.2 行 | G-014 #10-13 |
| 87 | `entity:unload/reason*` ⚠ → **D 落定**（离开跟踪集合推导，无 RemovalReason） | §6.2 行 | G-014 §D4 |
| 88 | `player:interactBlock/interactEntity` 的 `offhand/result` ⚠ → **D 落定**（sink 不携带 hand；前置可取消 C2S 时序相反） | §6.2 行 | G-014 §D2/§D3 |
| 89 | `key:press/parse` A 维持注（证据 §1） | §6.2 行 | G-015 回写清单 |
| 90 | `game:start/playerName*` ⚠ → **D 落定**（三重拿不到 + 穿透层等价配方） | §6.2 行 | G-015 §D1 |
| 91 | `player:death`：`inventory` 快照 → 已实施 B + `respawn` A 面尚未实现注 | §6.2 行 | G-014 #9 + 遗留 |
| 92 | 新增 **D 行 ×7**：`EventMouseScroll.deltaX`、`EventRecvMessage.signature/messageType`、`EventBlockUpdate.block`、`EventBossbar.bossBar/type`、`EventJoinServer.player`、`EventPlayerLeave.player`（各附 §D 证据） | §6.2 新行 | G-015 §D2-D7/D9/D10 |
| 93 | 新增 **A 行 ×7 组**：`updateType`、`screenName`、`dimension`、`sound/position`、`ChunkUnload.x/z`、`PlayerJoin/Leave.UUID`、`SignEdit.signText` | §6.2 新行 | G-015 回写清单 A 行组 |
| 94 | §7 T-517 状态 → G-031 已实施（ConnectionMixin 落点偏离注） | §7 backlog 表行 | G-002 §13-5 + G-031 遗留 3/4 |

**13 号合计：4（§2.2）+ 7（§2.3）+ 11（§3.2）+ 9（§4）+ 15（§5）+ 18（§6）= 64 条**（清单行口径；D 行 ×7/A 行 ×7 组各按 1 条计）。

## 三、未回写项与原因（18 条）

| # | 未回写项 | 来源 | 原因 |
|---|---|---|---|
| 1 | 12 §5.3 `screen:signEdit` payload 行补 `pos` | G-015 回写清单 | 原文为"**若列** `screen:signEdit` payload 行补 `pos`"——12 §5.3 原无该行，条件不成立未新增；事件与 `pos` 落地事实已记入 13 §6.1 SignEdit 行 |
| 2 | 12 号 `LocalPlayer.interactions` 入口名拍板 | G-028 偏离 1 | 报告原文"入口名登记本报告**待拍板**回写"——未拍板，仅在 13 §5.2 行内登记"12 号未拍板"注，12 号不动 |
| 3 | 层 z 序裁定（`layer(id,{z?})`/`setZIndex`/端口 `ConcurrentSkipListMap<(z,id)>`） | G-033 偏离②/遗留 3、G-033B 遗留 5 | 报告原文"需**协调者裁定**是否给 G-033 立补卡"——待裁定，不落文档 |
| 4 | 15 号任务卡 / `gen-development-plan.mjs` TASKS 表 / `gap/tasks.json` targets（G-001 §12 回写 4/5、G-002 §13、G-003 §13-3、G-031 遗留 4、G-032B/G-034/G-034B/G-034C 遗留 1、G-035 落点偏离等） | 多报告 | 属 15 号车道（15 号回写由另一 agent 负责）；生成物按既定流程走 `gen-development-plan.mjs` 重生成，禁手改 |
| 5 | `plan/TASKS.md` 看板行（G-001 遗留 2、G-010 L2、G-012 L6、G-016 L9、G-037 遗留 5 等） | 多报告 | 本任务严禁改 `plan/TASKS.md` |
| 6 | README 决策登记（G-002 §13-2；12 号头部"变更须回写 README"规则） | G-002 等 | README 不在本任务两文件范围 |
| 7 | NeoMacros-Docs d.ts/类页/事件目录页生成物（G-002 §13-6、G-013/014/015/016 遗留、各报告"随合并 CI 重生成"） | 多报告 | 生成物随合并 CI 重生成，非 docsx 手改对象 |
| 8 | 13 §4 特化类名列 `XxxInventoryEntity` 与落地类名 `XxxInventory` 的出入 | （观察项） | G-019/G-020/G-021 均按 `XxxInventory` 称呼但无报告点名要求改此列；涉命名判断，按"不新增判断"跳过，建议后续文档批统一 |
| 9 | 13 §5.2 `getStatistics` 行仍写 `Record<statId, number>` + `stat(statId)`，与 12 号第七轮 `Statistics` 句柄重塑不一致 | （观察项） | 无报告点名回写此行，跳过（已知漂移，供后续批次） |
| 10 | G-036 `initially` int 单位、G-036B 变参缩水、G-035 `showMessage`/泛 truthy 等 compat 行为回写 | G-035/G-036/G-036B | 报告原文"G-004/005 波**实测后**回写 13 号"——compat 实测未发生，时点未到 |
| 11 | builder 类名取舍（`CyclicButtonBuilder` vs `CycleButtonBuilder`） | G-036B 偏离 5 | "若协调者不改则无需回写"——未拍板改名 |
| 12 | G-001 §12 回写 6 `plan/TASKS.md` 标 done 挂报告 | G-001 | 禁改 `plan/TASKS.md` |
| 13 | G-002 报告 §2.2 正文 `handshaking` 笔误本身 | G-031 遗留 5 | 笔误在 `plan/reports/G-002.md`（报告文件，非 12/13 文档）；12 §5.3 新写内容已用 `handshake`；报告原文不属本任务改动面 |
| 14 | 13 号头部追加修订行 | 任务规则 | 任务仅要求 12 号头部按体例追加第八轮修订行，13 号未加 |
| 15 | `invisible` 上提 `Entity` 基类（`setInvisible` 同批核对） | G-022 §6.1 | 报告定性为"回写**建议**：后续实体基类泳道"——建议已记入 13 §3.2 行内；基类改动本身属代码任务 |
| 16 | `game:resourcePack` 保留 `id?` 选项 | G-016 L1 | 按草稿以 `isGameStart, loadedPacks` 替换已回写；"如需保留 `id?` 待有数据源的任务承接"——无数据源，不落 |
| 17 | `getCraftableRecipes`/`getRecipes(boolean)` 2 行转 B | G-019 遗留 1 → G-020 遗留 1 | 承接方 G-020 按任务卡口径**继续遗留**（Recipe 句柄类型跨任务建设）；已在 13 §4 行内回写状态注，行本身维持 ⚠ 未转 B |
| 18 | G-025/G-029 偏离与回写节、G-018/G-018B/G-024 族 | 各报告 | 逐份核对后无 docsx（12/13）落点条目——G-025/G-029 偏离均为代码/compat 面，无需改文档 |

## 四、统计与交付

- **回写条数：12 号 30 条 / 13 号 64 条 / 未回写 18 条**（原因见上表）
- 备份：`plan/reports/docsx-backup-20261114/12-api-catalog.md`、`plan/reports/docsx-backup-20261114/13-jsmacros-gap-api.md`
- 本清单：`plan/reports/DOCS-WRITEBACK-1213.md`（`git add -f` 单文件提交）
