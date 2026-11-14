# 13 · JsMacros 缺口 API 补全目录（NeoMacros API 面扩展真源）

> 定位：[jsmacros-compat](../jsmacros-compat/) 的 `docs/missing-apis.md`（对比基线 JsMacros-CE 1.9.2，约 1341 项）记录了兼容壳无法实现的面，其根因几乎全部是 **NeoMacros 未暴露对应底层能力**。本分册是这些缺口的 **API 面扩展设计真源**：逐项给出 NeoMacros 成员命名（**Mojang 官方命名优先，其次 NeoMacros 规范**）、Mojmap 依据、归宿判定与实施分期。兼容壳回桥后，missing-apis.md 对应条目随 audit 重生成而消失。
> 与 [12 号](12-api-catalog.md) 的关系：12 号是全模块 API 面的总真源；13 号是其缺口驱动的增补分册——所有新成员命名与形态规则沿用 12 号（双层模型 / 统一活句柄 / 缺席=`undefined` / Promise 异步 / 事件 `domain:camelCase`），冲突时以 12 号 + 本分册最新拍板为准。
>
> **2026-11-06 需求方拍板（不可单方面推翻，变更须回写本文与 [README](README.md) 决策登记）：**
> ① **目标层 = NeoMacros 扩展 + 兼容壳回桥**——先在 NeoMacros 落地底层能力，jsmacros-compat 再把 `UnimplementedError` 桩改为桥接实现；
> ② **首批开发批次 = 全局库全量**（missing-apis §1 的 13 个库，~138 项）；
> ③ **推翻 12 号"物种不特化"**——恢复实体物种子类树（§3；容器特化树 2026-11-04 第二轮已恢复，见 §4）；
> ④ **永久缺口仅一项** = 运行时 Java 编译 / 动态类生成 / 运行时 Jar 加载（数据包、进程控制、脚本 GUI 不豁免，全部进分期排期）；
> ⑤ **命名铁律**：首要贴合 Mojang 官方命名，其次 NeoMacros 规范（细则见 §1）；
> ⑥ **GlobalVars 不支持**——不为其新增任何能力（含枚举），`GlobalVars.getRaw` 永久为桩（与 12 号既定"跨脚本共享 = 反模式"一致）。

> **2026-11-06 落地进度**（首推 @dbd1ccf CI 全绿）：§1 全局库已全部落地（T-502~T-506/T-508 首轮回桥）；§3 物种子类树已落地 21 类（Sheep/Wolf/Cat/Villager/Creeper/Zombie/PrimedTnt/Bat/MushroomCow/Enderman/Phantom/Spider/Horse/Donkey/Llama/AbstractHorse/AbstractChestedHorse/IronGolem/Chicken/Ghast/Ocelot/Rabbit/Strider/WitherBoss + Mob/Animal/TamableAnimal 结构层）；§4 FurnaceInventory 首类落地（menuSlot/containerState 通道）；§5 Chunk/findBlocks、BlockState 9 物理成员、Pos2D.ZERO/compare、client 全套扩展（连接/模组/窗口/剪贴板/注册表/截图）、java.wrap、FileHandle/Ws/Image 新类型；§6 world:join.address。实证裁剪：Ocelot.isTrusting/Strider 鞍抖/Chicken.egg/AbstractChestedHorse.hasChest/Wither.isInvulnerable/Creeper.getFuse/Wolf 心情族/Pig 鞍 = 原版非公开或组件化（后随批需 accessor/组件专项）。

## 0. 方法论：缺口归宿判定

每个缺口条目归入五类之一，映射表用"归宿"列标注：

| 归宿 | 含义 | 处理 |
|---|---|---|
| **A** | 桥接现有面 | NeoMacros 已有能力（12 号【已有】或已实施），仅兼容壳回桥即可 |
| **B** | 新增包装成员 | 在现有模块/类上加成员（ports → facade → adapter 竖切） |
| **C** | 新增类/类型 | 新 guest 类（`@ApiClass(kind=HANDLE/DATA, wraps=…)`) 或新事件 |
| **D** | 不映射 | JsMacros 专有概念 / 既定反模式 / 穿透层已有等价配方（§2 清单，写明理由） |
| **E** | 永久缺口 | 拍板④唯一项：运行时 Java 编译/动态类生成/Jar 加载（§2） |

条目数量速览（missing-apis → 本分册）：§1 全局库 139 项（首批）；§2 实体基类 ~60 项；§3 物种壳 75 类；§4 容器壳 18 类；§5 支持类 30 类 ~600 项；§6 事件 19 事件名 + 24 字段 + 4 手动条目；§7 内联调用点 16 项。除 §1 外全部进 §7 分期（T-509+）。

## 1. 命名规范（Mojang 优先推导规则）

在 12 号 §4.0 命名硬规则（camelCase 一律 / 无参状态属性化 / 名词不作动词 / 异步 Promise）之上，增补 Mojang 优先细则：

1. **无参状态查询**：Mojang `getXxx()` → NeoMacros 属性 `xxx`（`Level.getMoonPhase` → `world.moonPhase`；`LivingEntity.getAbsorptionAmount` → `absorption`）。带参谓词保留 `is/has/can` 前缀（`isEnchanted` → 方法 `isEnchanted()`）。
2. **动作成员**：保留 Mojang 方法原词（camelCase），不臆造动词——`MouseHandler.grabMouse()` → `client.grabMouse()`；`Screenshot.grab()`（静态工具）→ `client.grabScreenshot()`（"screenshot" 是名词，作动词违铁律，取 Mojang 动词 grab）。
3. **类名**：实体物种子类 = Mojang 类名原词 + **`Entity` 后缀**（`VillagerEntity`/`SheepEntity`/`PrimedTntEntity`/`EndermanEntity`/`WitherBossEntity`/`MushroomCowEntity`；2026-11-07 需求方补令，推翻本条最初的"Mojmap 原词裸名"——统一 Entity 后缀避免挂根后的泛名歧义；不用 JsMacros 的 `XxxEntityHelper` 命名，也不用社区俗名 Mooshroom）；容器特化类名沿第二轮拍板的 `XxxInventory` 形态（见 §4 注）。
4. **wraps 标注**：一律 Mojmap 全限定类名（`@ApiClass(wraps = "net.minecraft.world.entity.npc.Villager")`）。26.x 节点直用官方名；1.21.6 节点 Mojmap+Parchment。
5. **JsMacros 词汇一律不沿用**：`getReach` → `blockInteractionRange`/`entityInteractionRange`（Mojang 原词）；`getContainedItemStack` → `stack`（12 号已定）；`getWorldScanner` → `world.findBlocks(options)`（扫描 DSL 用 options 对象，不设 builder 工厂链——JsMacros 病灶 #2 对策）。
6. **⚠ 标记**：Mojmap 名随版本漂移的成员（26.x 重命名/迁移管线的）标 ⚠，实施时以 `versions/26.3` 工具链反编译 jar（javap / sources）核对为准，核对后去掉标记。
7. **新事件名**：`domain:camelCase`（12 号 §5.1，2026-11-04 确认维持 camelCase）。

## 2. 永久缺口与不映射清单（归宿 D/E）

### 2.1 永久缺口（E，拍板④唯一项）

| JsMacros 面 | 条目 | 理由 |
|---|---|---|
| `FReflection` 编译族 | `compileJavaClass` `getAllCompiledJavaClassVersions` `getClassFromClassBuilderResult` `getCompiledJavaClass` `createClassBuilder` `createLibrary` `createLibraryBuilder` `loadJarFile` | 运行时 Java 源编译 / 动态类生成 / Jar 加载属重量级宿主能力；`neo.java` + GraalJS 互操作已覆盖绝大部分场景（动态代理=`java.proxy`、类型互操作=`java.type`） |

### 2.2 不映射（D）

| JsMacros 面 | 条目 | 理由 |
|---|---|---|
| `FJsMacros` 脚本运行时管理 | `getProfile` `getConfig` `getServiceManager` `getOpenContexts` `runScript` `wrapScriptRun` `wrapScriptRunAsync` `open` `openUrl` | JsMacros 脚本运行时内部机制（profile/service/嵌套运行/系统应用拉起），NeoMacros 模块体系无对应物 |
| `FGlobalVars` | `getRaw`（全族不扩展） | 拍板⑥：跨脚本共享 = 反模式（12 号既定），`neo.storage` 按脚本隔离且不提供枚举；兼容壳 `getRaw` 桩永久保留 |
| `FChat.getLogger` | — | mod 日志不暴露给脚本；`chat.log` 覆盖本地输出 |
| `FUtils.open/openUrl/openFile` | — | deprecated in JsMacros；系统应用拉起不设 |
| `FUtils.guessName/guessNameAndRoles` | — | `player.self.name` 覆盖 |
| `FUtils.getHash/hashString` | — | 杂物抽屉库不设（12 号原则 #1）；穿透层配方：`java.type('java.security.MessageDigest')` |
| `FJavaWrapper` | `deferCurrentTask` `getCurrentPriority` `stop` | NeoMacros 异步模型无脚本线程任务队列（JsMacros 病灶 #10）；等价物 = `thread.sleep/nextTick` + `Promise` |
| `FReflection` 映射助手 | `loadMappingHelper` `loadCurrentMappingHelper` | 内建 `MappingResolver` 翻译是代差替代（12 号 §4.9），无需脚本侧助手 |
| `FClient.getModLoader` `getRegistryManager` | — | 单一加载器不设查询；registry 原始访问走穿透层（`java.type`）或 §5.4 `client.registry` 只读面 |
| `FWorld.getRaw` | — | 穿透层自取：`java.type('net.minecraft.client.Minecraft').getInstance().level`（12 号 §6.1 对照表既有口径） |
| `FEntityHelper.asServerEntity` | — | 服务端实体对客户端不可见（语义拒绝） |
| `FPlayer.setGameMode` | — | 客户端无写权；等价 `chat.say('/gamemode …')` |
| 输入覆盖显示 | ``setDrawPredictionsEntity`` `predictInput` `predictInputs` | JsMacros 自有输入覆盖器专属机制，非原版能力 ⚠（实施 W3 时若确认可经 `LocalPlayer` 显示层复刻再升级为 B，默认 D） |
| 事件名 | ``WrappedScriptEntity`` `Service` `ProfileLoad` `CommandContext` | JsMacros 脚本运行时内部事件（嵌套运行/服务/命令派发） |
| 事件手动条目 | `BaseEvent.trigger()` `EventRecvMessage.text (write)` `EventSendMessage.message (write)` `EventCustom.cancelable (write)` | 维持现状：重触发不可能；改写不支持（唯一取消语义）；自定义事件恒不可取消 |

### 2.3 服务端-only 物种状态（T-522 定案：D，2026-11-11）

以下 JsMacros 物种成员在客户端世界实体上**没有可观察值**（服务端权威状态或纯服务端字段），
按"客户端 mod 哲学"不设成员、不挖 accessor mixin；脚本需要时只能经服务端插件/协议层获取。
同一批里已落地的客户端可观察部分见 §3.2 末的 T-522 落地清单。

| 物种（JsMacros 壳） | 未落成员 | 理由 |
|---|---|---|
| `MobEntityHelper` | `isAttacking`（`getTarget` 已发布但客户端恒 empty） | 攻击目标仅服务端 AI 写入；客户端 synched data 无该字段 |
| `FoxEntityHelper` | `canTrust` `getOwner` `getSecondOwner` `getTrustedUUIDs` `hasFoundTarget` `isDefending` `isWandering` `isJumping` | 信任列表/记忆（Brain Memory）仅服务端；`isJumping` 客户端有同名 pose 但语义为跳跃动画而非 AI 状态 |
| `AbstractHorseEntityHelper` | `getHealthStat/getHorseJumpHeight/getHorseSpeed/getJumpStrengthStat/getMaxHealthStat/getMaxJumpStrengthStat/getMaxSpeedStat/getMinHealthStat/getMinJumpStrengthStat/getMinSpeedStat/getSpeedStat` `canBeSaddled` `canWearArmor` | 属性（Attribute）基准值仅服务端下发；客户端只同步 current 值。`isSaddled` 已落地（装备槽 SADDLE 同步） |
| `MerchantEntityHelper` / `VillagerInventory` trades | `getTrades` `refreshTrades` `getExperience` `hasCustomer` | 交易报价仅在打开交易界面时经 `MerchantOffers` 包下发，自由观察态不可得 |
| `CreeperEntityHelper` | `getFuseChange` `getFuseTime` `getMaxFuseTime` `getRemainingFuseTime` | 引信仅服务端字段；客户端只有 `swellDir`/`swell`（已落地） |
| `WardenEntityHelper` | `getAnger`（细粒度）、`isChargingSonicBoom` `isDigging` `isEmerging` `isRoaring` `isSniffing` | anger 仅 `angerLevel` 枚举同步（已落地 `angerLevel`）；其余为服务端动画状态机 |
| `ParrotEntityHelper` | `isFlying` `isSittingOnShoulder` `isStanding` | 肩停拥状态在 `Player` 侧同步而非 parrot 实体；飞行姿态无公有访问器 |
| `AllayEntityHelper` | `canDuplicate` | 复制判定在服务端 AI |
| `PiglinEntityHelper` | `hasCrossbowReady` `isAdmiring` `isChargingCrossbow`(独立) `isMeleeAttacking` `isWandering` | Brain 活动仅服务端；`Pillager.isChargingCrossbow` 已有客户端等价 |
| `SnowGolemEntityHelper` | `isShearable` 已落地（`readyForShearing`），无剩余 | — |
| `EndermanEntityHelper` | `isHoldingBlock` 由 `carriedBlock !== undefined` 组合（A，不设成员） | 组合语义交脚本 |
| `ZombieVillagerEntityHelper` | `getLevel`（僵尸村民无职业等级，原值为 0 常量） | JsMacros 侧亦为无意义值 |
| `VindicatorEntityHelper` | `isJohnny` | 私有字段 `isJohnny`，无公有访问器（不挖 mixin） |
| `MinecartFurnaceEntityHelper` | `isPowered` | `hasFuel()` 为 protected；客户端无同步字段 |
| `ArrowEntityHelper` | `isShotFromCrossbow` | 无公有访问器；`isCritArrow`/`getPierceLevel` 已落地 |
| `TridentEntityHelper` | `hasLoyalty`（忠诚附魔等级） | 需读物品附魔组件；`isEnchanted` 以 `foil` 落地 |
| `BoatEntityHelper` | `getBoatType`/`getBoatBlockType` | 26.x 木质类型并入 EntityType（`type` 串判别，A 不设成员）；`getLocation` 状态枚举无公有访问器 |
| `DisplayEntityHelper` | `getLerpTargetX/Y/Z/Yaw/Pitch` `getLerpProgress` `getSkyBrightness` `getBlockBrightness` | 26.x 插值器为私有 `GenericInterpolator`；`brightness`（打包覆盖值）与 `viewRange/shadow*` 已落地 |
| `TextDisplayEntityHelper` | `getData`（整包 DATA 结构） | 拆为 `text`/`backgroundColor`（已落地）；行宽/不透明度留待按需 |
| `InteractionEntityHelper` | `setCanHit`（写）、`shouldRespond`（读已落地为 `responsive`） | 客户端写交互实体无意义（服务端权威） |

## 3. 实体物种子类树（推翻浅类树，拍板③）

### 3.1 设计规则

1. **类名 = Mojang 类名原词 + `Entity` 后缀**（已带 Entity 结尾的名字不重复加），wraps = 对应 Mojmap FQN（原词不变）；`kind=HANDLE`，实现 `ScriptHandle`，失效语义沿 12 号 §2.3（属性 `undefined`、方法 throw）。
2. **镜像 Mojang 层级**：从 `Entity` 到每个注册叶子的父链若存在未注册的 Mojang 中间类，**自动生成结构性中间类**（无自有成员，仅保证 `extends` 链与 `instanceof` 正确；2026-11-07 起类全部直接挂 `neo` 根）。深度跟随 Mojang 层级（12 号"深度封顶 4 层"随之修订为"跟随 Mojang 层级深度"）。
3. **派发**：句柄解析 Mojang 实体运行时类 → 沿父类链匹配最深已注册 guest 类（引擎 glue 现有 `__neoDefineClass` 树上追加，机制同 T-401）。
4. **成员归属**：缺口成员挂在 JsMacros 对应壳类所映射的 Mojang 类层上；共享成员上提到双方共同的最深 Mojang 公共父类（如 `AbstractHorse` 的骑乘统计族）。
5. **12 号回写**：§2.1 类树图与"物种不特化"段、§1 病灶 #2 行同步修订；README 决策登记加行。

### 3.2 物种映射表（JsMacros 壳类 → NeoMacros guest 类）

结构性中间类（自动生成，无成员）在此列出以明确 extends 链：`ProjectileEntity`、`AbstractHurtingProjectileEntity`、`AbstractArrowEntity`、`AbstractMinecartEntity`、`HangingEntity`、`LightningBoltEntity`（不注册，无缺口）、`AmbientCreatureEntity`、`PathfinderMobEntity`、`AgeableMobEntity`、`WaterAnimalEntity`、`AbstractGolemEntity`、`AbstractChestedHorseEntity`、`MonsterEntity`、`EnemyEntity`（按需最少集合）。

| JsMacros 壳 | NeoMacros 类 | wraps（Mojmap FQN，省略 `net.minecraft.world.entity.`） | 缺口成员（→ 成员名） | 批次 |
|---|---|---|---|---|
| ArmorStandEntityHelper | ``ArmorStandEntity`` | `decoration.ArmorStand` | getBody/Head/LeftArm/RightArm/LeftLeg/RightLegRotation → `bodyPose* headPose* leftArmPose* rightArmPose* leftLegPose* rightLegPose*`（`getPose` 数据）、hasArms→`showArms*`、hasBasePlate→`showBasePlate*`、isMarker→`isMarker*`、isSmall→`isSmall*`、isVisible→`invisible*`（`setInvisible` 已在 Entity 面 ⚠核对）、toArray→`poses()` | T-509 |
| ArrowEntityHelper | ``ArrowEntity`` | `projectile.Arrow` | getColor→`color*`、getPiercingLevel→`piercingLevel*`、isCritical→`isCritical*` ⚠、isShotFromCrossbow→`shotFromCrossbow*` | T-509 |
| TridentEntityHelper | ``TridentEntity`` | `projectile.ThrownTrident` | hasLoyalty→`loyalty*`?（Mojang 字段 `loyalty`? 经 enchant 读取 ⚠）、isEnchanted→`isEnchanted*` ⚠ | T-509 |
| FishingBobberEntityHelper | ``FishingHookEntity`` | `projectile.FishingHook` | getHookedEntity→`hookedEntity*`、hasCaughtFish→`isCaughtInWater`? ⚠（Mojang 字段 `biting`/`timeUntilHookedTraveling` ⚠核对）、hasEntityHooked→`hookedEntity !== undefined`、isInOpenWater→`openWater*` | T-509 |
| AreaEffectCloudEntityHelper | ``AreaEffectCloudEntity`` | `AreaEffectCloud` | getColor→`color*`、getParticleType→`particle*`（particleOptions→id）、getRadius→`radius*`、isWaiting→`isWaiting*` ⚠ | T-509 |
| BoatEntityHelper | ``BoatEntity`` | `vehicle.Boat` | getBoatBlockType→`blockId*`?（`getBlock` ⚠）、getBoatType→`woodTypeId*` ⚠、getLocation→`status*`（`Status` 枚举串）、isChestBoat→`hasChest*` ⚠（chest boat 独立类 ChestBoat——见注）、isInAir/InWater/OnLand/Underwater→`status()` 判别或独立属性 | T-509 |
| EndCrystalEntityHelper | ``EndCrystalEntity`` | `item.EndCrystal` ⚠（FQN 待核对） | getBeamTarget→`beamTarget*`、isNatural→`showBottom*`（Mojang `setShowBottom`） | T-509 |
| FallingBlockEntityHelper | `FallingBlockEntity` | `item.FallingBlockEntity` | getBlockState→`blockState*`、getOriginBlockPos→`blockPos*`（`getStartPos` ⚠） | T-509 |
| InteractionEntityHelper | ``InteractionEntity`` | `Interaction` | getWidth/getHeight→`width* height*`、getLastAttacker→`lastAttacker*`、getLastInteracted→`lastInteraction*` ⚠、setCanHit→`responsive*`（`setResponsive`）、shouldRespond→`getResponse?` ⚠ | T-509 |
| ItemFrameEntityHelper | ``ItemFrameEntity`` | `decoration.ItemFrame` | getItem→`item*`、getRotation→`itemRotation*`? ⚠、isGlowingFrame→（GlowItemFrame 子类判别） | T-509 |
| PaintingEntityHelper | ``PaintingEntity`` | `decoration.Painting` | getIdentifier→`artId*`? ⚠（variant）、getWidth/getHeight→`width* height*` | T-509 |
| DisplayEntityHelper | ``DisplayEntity`` | `Display` | getBillboardMode→`billboard*`、getBrightness→`brightnessOverride*` ⚠、getBlockBrightness→`blockBrightness`? ⚠、getDisplayWidth/Height→`width* height*`、getGlowColorOverride→`glowColorOverride*`、getLerpProgress→`lerpProgress*` ⚠、getLerpTargetX/Y/Z/Pitch/Yaw→`lerpTarget` 族、getShadowRadius/Strength→`shadowRadius* shadowStrength*`、getSkyBrightness→`brightnessOverride` 合并 ⚠、getViewRange→`viewRange*`、getVisibilityBoundingBox→`boundingBox*` | T-509 |
| BlockDisplayEntityHelper | ``BlockDisplayEntity`` | `display.BlockDisplay` | getBlockState→`blockState*` | T-509 |
| ItemDisplayEntityHelper | ``ItemDisplayEntity`` | `display.ItemDisplay` | getItem→`item*`、getTransform→`transform*`（`ItemTransforms` 数据 ⚠形态） | T-509 |
| TextDisplayEntityHelper | ``TextDisplayEntity`` | `display.TextDisplay` | getData→`text* backgroundColor* lineAlign?`（`readTextDisplayData` 拆解 ⚠） | T-509 |
| FurnaceMinecartEntityHelper | ``MinecartFurnaceEntity`` | `vehicle.MinecartFurnace` | isPowered→`hasFuel*` | T-509 |
| TntMinecartEntityHelper | ``MinecartTNTEntity`` | `vehicle.MinecartTNT` | getRemainingTime→`fuse*`、isPrimed→`isPrimed*` ⚠ | T-509 |
| TntEntityHelper | ``PrimedTntEntity`` | `item.PrimedTnt` | getRemainingTime→`fuse*` | T-509 |
| MobEntityHelper | ``MobEntity`` | `Mob` | isAiDisabled→`noAi*`、isAttacking→`target* !== undefined`（`getTarget`） | T-509 |
| IllagerEntityHelper | ``AbstractIllagerEntity`` | `monster.AbstractIllager` | getState→`illagerArmPose*`? ⚠（`getCurrentArms`/`ArmPose`）、isCelebrating→`isCelebrating*` | T-509 |
| SpellcastingIllagerEntityHelper | ``SpellcasterIllagerEntity`` | `monster.SpellcasterIllager` | getCastedSpell→`spell*`（`getCurrentSpell`）、isCastingSpell→`isCastingSpell*` | T-509 |
| PillagerEntityHelper | ``PillagerEntity`` | `monster.Pillager` | isCaptain→`isCaptain*` | T-509 |
| VindicatorEntityHelper | ``VindicatorEntity`` | `monster.Vindicator` | isJohnny→`isJohnny*` | T-509 |
| AbstractPiglinEntityHelper | ``AbstractPiglinEntity`` | `monster.piglin.AbstractPiglin` | canBeZombified→`canBeZombified*` | T-509 |
| PiglinEntityHelper | ``PiglinEntity`` | `monster.piglin.Piglin` | hasCrossbowReady→`isChargingCrossbow` 判别族 ⚠、isAdmiring→`isAdmiring*` ⚠、isDancing→`isDancing*`、isMeleeAttacking→`isMeleeAttacking*` ⚠、isWandering→`isWandering*` ⚠（Brain 活动查询 ⚠核对） | T-509 |
| WitchEntityHelper | ``WitchEntity`` | `monster.Witch` | getPotion→`usingItem*` ⚠、isDrinkingPotion→`isDrinkingPotion*` | T-509 |
| WitherEntityHelper | ``WitherBossEntity`` | `boss.WitherBoss` | getRemainingInvulnerableTime→`invulnerableTime*`、isFirstPhase→（`getAlternativeTarget`/phase 查询 ⚠）、isSecondPhase→同上 ⚠、isInvulnerable→`isInvulnerable*` | T-509 |
| WitherSkullEntityHelper | ``WitherSkullEntity`` | `projectile.WitherSkull` | isCharged→`isCharged*` | T-509 |
| ZombieEntityHelper | ``ZombieEntity`` | `monster.Zombie` | isConvertingToDrowned→`isConverting*` ⚠ | T-509 |
| ZombieVillagerEntityHelper | ``ZombieVillagerEntity`` | `monster.ZombieVillager` | getLevel→（villager 数据 absent ⚠——僵尸村民无职业等级，D 评估）、getProfession→（同上 D 评估）、getVillagerBiomeType→`villagerData*`? ⚠、isConvertingToVillager→`isConverting*` | T-509 |
| VillagerEntityHelper | ``VillagerEntity`` | `npc.Villager` | getLevel→`villagerData.level*` ⚠（VillagerData 拆解）、getProfession→`villagerData.profession*` ⚠、getStyle→`villagerData.variant*` ⚠ | T-509 |
| MerchantEntityHelper | ``AbstractVillagerEntity`` | `npc.AbstractVillager` | getExperience→`trades` 附带? ⚠、getTrades→`trades*: TradeOffer[]`（C 新增 TradeOffer 数据类）、hasCustomer→`isTrading*` ⚠、refreshTrades→D（客户端不可写） | T-510（随容器 trades） |
| GuardianEntityHelper | ``GuardianEntity`` | `monster.Guardian` | getTarget→`target*`、hasSpikesRetracted→`isActive*`? ⚠（`spikesRetracted`? ⚠核对）、hasTarget→`target !== undefined`、isElder→（ElderGuardian 判别：`type === 'minecraft:elder_guardian'`——A，不建子类） | T-509 |
| ShulkerEntityHelper | ``ShulkerEntity`` | `monster.Shulker` | getAttachedSide→`attachFace*` ⚠（Direction）、getColor→`color*`? ⚠（DyeColor）、isClosed→`getRawPeekAmount` 判别 ⚠ | T-509 |
| SlimeEntityHelper | ``SlimeEntity`` | `monster.Slime` | getSize→`size*`、isSmall→`size <= 1` | T-509 |
| PhantomEntityHelper | ``PhantomEntity`` | `monster.Phantom` | getSize→`size*` | T-509 |
| SpiderEntityHelper | ``SpiderEntity`` | `monster.Spider` | isClimbing→`isClimbing*` | T-509 |
| VexEntityHelper | ``VexEntity`` | `monster.Vex` | isCharging→`isCharging*` | T-509 |
| GhastEntityHelper | ``GhastEntity`` | `monster.Ghast` | isShooting→`isCharging*` ⚠ | T-509 |
| StriderEntityHelper | ``StriderEntity`` | `monster.Strider` | isSaddled→`isSaddled*`、isShivering→`isShivering*` | T-509 |
| DrownedEntityHelper | ``DrownedEntity`` | `monster.Drowned` | hasNautilusShell→D 评估 ⚠（JsMacros 自有合成判断）、hasTrident→`getMainHandItem().is('minecraft:trident')`（A 组合） | T-509 |
| CreeperEntityHelper | ``CreeperEntity`` | `monster.Creeper` | getFuseChange→D（内部状态）、getFuseTime/getMaxFuseTime/getRemainingFuseTime→`fuse*`（`getFuse`）+ 上限常量 30 → `maxFuse*`、isCharged→`isPowered*`、isIgnited→`isIgnited*` ⚠ | T-509 |
| EnderDragonEntityHelper | ``EnderDragonEntity`` | `boss.enderdragon.EnderDragon` | getBodyPart/Parts→D 评估（Part 列表形态）、getPhase→`phase*`? ⚠（EnderDragonPhaseManager ⚠） | T-509 |
| EndermanEntityHelper | ``EnderManEntity`` | `monster.EnderMan` | getHeldBlock→`heldBlock*`（BlockState）、isHoldingBlock→`heldBlock !== undefined`、isProvoked→`isProvoked*` ⚠、isScreaming→`isCreepy*` ⚠ | T-509 |
| IronGolemEntityHelper | ``IronGolemEntity`` | `animal.IronGolem` | isPlayerCreated→`playerCreated*` | T-509 |
| SnowGolemEntityHelper | ``SnowGolemEntity`` | `animal.SnowGolem` | hasPumpkin→`hasPumpkin*`、isShearable→`readyForShearing*`? ⚠（Shearable） | T-509 |
| MooshroomEntityHelper | ``MushroomCowEntity`` | `animal.MushroomCow` | isBrown→`variant === 'brown'`（MushroomCow.MushroomType）、isRed→同上、isShearable→`readyForShearing*` ⚠ | T-509 |
| AllayEntityHelper | ``AllayEntity`` | `animal.allay.Allay` | canDuplicate→`canDuplicate*` ⚠、isDancing→`isDancing*`、isHoldingItem→`getMainHandItem` 判别 | T-509 |
| AnimalEntityHelper | ``AnimalEntity`` | `animal.Animal` | canBreedWith→`canFallInLove`? ⚠、isFood→`isFood*`（`isFood(ItemStack)` 带参谓词） | T-509 |
| AxolotlEntityHelper | ``AxolotlEntity`` | `animal.axolotl.Axolotl` | getVariantId→`variant*`（Axolotl.Variant 串）、getVariantName→`variant`、isFromBucket→`fromBucket*`? ⚠（Bucketable）、isPlayingDead→`isPlayingDead*` | T-509 |
| BatEntityHelper | ``BatEntity`` | `ambient.Bat` | isResting→`isResting*` | T-509 |
| BeeEntityHelper | ``BeeEntity`` | `animal.Bee` | hasNectar→`hasNectar*`、hasStung→`hasStung*`、isAngry→`isAngry*` | T-509 |
| DolphinEntityHelper | ``DolphinEntity`` | `animal.Dolphin` | getMoistness→`moistness*`、getTreasurePos→`treasurePos*`、hasFish→`gotFish*` ⚠ | T-509 |
| FrogEntityHelper | ``FrogEntity`` | `animal.frog.Frog` | getTarget→`target*`（`tongueTarget` ⚠）、getVariant→`variant*`（FrogVariant）、isCroaking→`isCroaking*` | T-509 |
| GoatEntityHelper | ``GoatEntity`` | `animal.goat.Goat` | hasHorns/LeftHorn/RightHorn→`hasLeftHorn* hasRightHorn*`（`isScreamingGoat` 区分尖叫羊 ⚠核对成员形态）、isScreaming→`isScreamingGoat*` ⚠ | T-509 |
| FoxEntityHelper | ``FoxEntity`` | `animal.Fox` | canTrust→`trusts(uuid)`（带参谓词，`getTrustingPlayers` ⚠）、getItemInMouth→`mouthItem*` ⚠、getOwner/getSecondOwner→`trustedOwners*`? ⚠（`getUUIDs`? Fox TrustedAgents ⚠核对）、getTrustedUUIDs→同上、hasFoundTarget→D 评估 ⚠、isDefending→`isDefending*`、isJumping→`isPouncing*`? ⚠区分、isPouncing→`isPouncing*`、isRedFox→`variant === 'red'`（Fox.Type）、isSitting→`isSitting*`（Mojang `isSitting` ⚠）、isSnowFox→`variant === 'snow'`、isWandering→`isWandering*` ⚠ | T-509 |
| RabbitEntityHelper | ``RabbitEntity`` | `animal.Rabbit` | getVariant→`variant*`（Rabbit.Variant）、isKillerBunny→`variant === 'killer_bunny'`? ⚠ | T-509 |
| PufferfishEntityHelper | ``PufferfishEntity`` | `animal.Pufferfish` | getSize→`puffState*`? ⚠（`getPuffState`） | T-509 |
| TropicalFishEntityHelper | ``TropicalFishEntity`` | `animal.TropicalFish` | getBaseColor→`baseColor*`? ⚠（variant 打包数据拆解）、getPatternColor→同上、getSize→（pattern 判别）、getVariant→`variant*`（打包 int）、getVarietyId→同上 | T-509 |
| FishEntityHelper | ``AbstractFishEntity`` | `animal.AbstractFish` | isFromBucket→`fromBucket*` ⚠（Bucketable） | T-509 |
| OcelotEntityHelper | ``OcelotEntity`` | `animal.Ocelot` | isTrusting→`trusting*` ⚠ | T-509 |
| PandaEntityHelper | ``PandaEntity`` | `animal.Panda` | getHiddenGene/getMainGene→`hiddenGene* mainGene*`（Panda.Gene 串）、getHiddenGeneName/getMainGeneName→同串直读、isBrown/isLazy/isLyingOnBack/isPlayful/isSitting/isSneezing/isWeak/isWorried→`isBrown* isLazy* isLyingOnBack* isSitting* isSneezing* isWeak* isWorried*` ⚠逐个核对、isHiddenGeneRecessive/isMainGeneRecessive→`isRecessive(gene)`? ⚠、isIdle/isScaredByThunderstorm/isPlaying→`isIdle* isScared* isPlaying*` ⚠ | T-509 |
| PigEntityHelper | ``PigEntity`` | `animal.Pig` | isSaddled→`isSaddled*` | T-509 |
| SheepEntityHelper | ``SheepEntity`` | `animal.Sheep` | getColor→`color*`（DyeColor）、isJeb→`isJeb*`? ⚠（custom name 判定 ⚠核对）、isShearable→`readyForShearing*` ⚠、isSheared→`isSheared*` | T-509 |
| TameableEntityHelper | ``TamableAnimalEntity`` | `TamableAnimal` | getOwner→`owner*`、isOwner→`isOwnedBy(entity)`（带参谓词）、isSitting→`isSitting*`? ⚠（orderedToSit）、isTamed→`isTame*` | T-509 |
| WolfEntityHelper | ``WolfEntity`` | `animal.Wolf` | getCollarColor→`collarColor*`（DyeColor）、isAngry→`isAngry*`、isBegging→`isBegging*`、isWet→`isWet*` | T-509 |
| CatEntityHelper | ``CatEntity`` | `animal.Cat` | getCollarColor→`collarColor*`、getVariant→`variant*`（CatVariant） | T-509 |
| AbstractHorseEntityHelper | ``AbstractHorseEntity`` | `animal.horse.AbstractHorse` | canBeSaddled→`isSaddleable*`、canWearArmor→`canWearArmor*`? ⚠（`getArmorType`? ⚠核对）、getHealthStat/getMaxHealthStat/getMinHealthStat→`maxHealthStat*`? ⚠（Attribute base/范围 ⚠核对形态）、getHorseJumpHeight/getJumpStrengthStat/…→`jumpStrengthStat*` 族 ⚠、getHorseSpeed/getSpeedStat/…→`speedStat*` 族 ⚠、getInventorySize→`inventorySize*` ⚠、getOwner→`ownerUUID*`、getSpeedStat→同上、isAngry→（仅 ZombieHorse/SkeletonHorse? ⚠核对）、isBred→`isBred*` ⚠、isEating→`isEating*`、isSaddled→`isSaddled*`、isTame→`isTame*` | T-509 |
| HorseEntityHelper | ``HorseEntity`` | `animal.horse.Horse` | getVariant→`variant*`（Horse.Variant 颜色 Markings ⚠形态） | T-509 |
| DonkeyEntityHelper | ``DonkeyEntity`` | `animal.horse.Donkey` | hasChest→`hasChest*` | T-509 |
| LlamaEntityHelper | `Llama`（含 `TraderLlama`） | `animal.horse.Llama` / `TraderLlama` | getCarpetColor→`carpetColor*`? ⚠（DyeColor）、getStrength→`strength*`、getVariant→`variant*`（Llama.Variant）、isTraderLlama→`instanceof TraderLlama` | T-509 |
| ParrotEntityHelper | ``ParrotEntity`` | `animal.Parrot` | getVariant→`variant*`（Parrot.Variant）、isFlying→`isFlying*` ⚠、isPartying→`isPartying*`? ⚠（party jukebox ⚠核对）、isSittingOnShoulder→`persistedText`? ⚠（Mojang: shoulder 状态分 setEntityOnShoulder ⚠核对）、isStanding→`!isSitting` | T-509 |

> 注：`ChestBoat` 是独立 Mojmap 类（`vehicle.ChestBoat`），缺口 `isChestBoat` 用 `instanceof ChestBoat` 判别——注册 `ChestBoat` 结构性子类即可，不设 `isChestBoat` 成员。同理 GlowItemFrame、ElderGuardian、TraderLlama 等判别型缺口一律走 `instanceof` / `type` 字符串，不设成员。

#### 3.2.1 T-522 落地状态（2026-11-11）

本表客户端可观察部分已全部落地（`@ApiSince("0.5.0")`），服务端-only 项按 §2.3 跳过。逐行状态：

- **已落地（新类）**：`ProjectileEntity` `AbstractArrowEntity` `ArrowEntity`（含 spectral_arrow）
  `ThrownTridentEntity` `WitherSkullEntity` `FishingHookEntity` `BoatEntity`（全木种 + raft）
  `ChestBoatEntity` `AbstractMinecartEntity` `MinecartFurnaceEntity` `MinecartTntEntity`
  `EndCrystalEntity` `InteractionEntity` `HangingEntity` `PaintingEntity` `DisplayEntity`
  `BlockDisplayEntity` `ItemDisplayEntity` `TextDisplayEntity` `SlimeEntity`（含 magma_cube）
  `GuardianEntity`（含 elder_guardian）`AbstractIllagerEntity` `SpellcasterIllagerEntity`
  `VindicatorEntity` `PigEntity`。
- **已落地（既有类补成员）**：Sheep `shearable`/`jeb`；Wolf `angry`/`wet`/`armor`；
  TamableAnimal `owner`/`tamed`/`sitting`；AbstractHorse `saddled`/`owner`；Strider
  `saddled`/`shivering`；Bee `angry`；Fox `variant`/`pouncing`/`asleep`/`faceplanted`/
  `crouching`；Frog `croaking`/`tongueTarget`；Panda `sitting`/`worried`/`playful`/`rolling`/
  `brown`/`weak`/`scared`；Parrot `partying`；Shulker `attachFace`/`peek`；SnowGolem 与
  MushroomCow `shearable`；Turtle `homePos`（≥26.2 节点）；Dolphin `hasFish`；Enderman
  `screaming`；Creeper `swellDir`/`swell`；AreaEffectCloud `particle`；Evoker/Pillager/Witch
  改挂 `AbstractIllagerEntity`/`SpellcasterIllagerEntity` 基类获得 `armPose`。
- **按 §2.3 跳过**：AI/Brain 状态、属性基准值、交易报价、引信倒计时、私有字段（johnny、
  hasFuel、isShotFromCrossbow）、插值器目标族。
- **`instanceof`/`type` 判别取代成员**：ChestBoat（同时注册独立类）、ElderGuardian、
  GlowItemFrame、TraderLlama、ZombieHorse/SkeletonHorse 等。

## 4. 容器特化树映射（对齐第二轮拍板）

第二轮已拍板"`Container` → `Inventory` 且恢复屏幕特化树（能力面差异特化 ≠ 数据差异特化，§8-12）"，规格细则在 `fabric-mod/api设计-补充与改进.md` §6.8（12 号 §4.7 引用）。本节把 missing-apis §4 的 18 个背包类逐个映射进该树；**特化类名沿第二轮拍板的 `XxxInventory` 形态**（命名对齐以 wraps 标注 Mojang Menu FQN 为准，本分册不重开命名）：

| JsMacros 类 | NeoMacros 特化类 | `kind` | wraps（Menu FQN，省略 `net.minecraft.world.inventory.`） | 缺口成员归宿 | 批次 |
|---|---|---|---|---|---|
| Inventory | `Inventory`（基类【已有】） | — | 抽象 | create/dragClick/findFree*/getHeld/getMap*/getRawContainer/getSlot*/getSlots/openGui/quickAll/swap/setSelectedHotbarSlotIndex/handler/inventory/man/map/mc/syncId → 见 §5.2 player 行（B，随容器专项统一评估；`getSlots`→`size`【已有】；`swap`→`swapHotbar`/`swapOffhand`【已有】） | T-510 |
| RecipeInventory | ``CraftingInventoryEntity`` | `'crafting'` | `CraftingMenu` | recipe book 族（getRecipes/getCraftableRecipes/getRecipeBookWidget/toggle/isOpened/setRecipeBook）→ `craftingRecipes()`/`recipeBook` 子面 ⚠ P2'；getCraftingWidth/Height/SlotCount→`width* height* slotCount*`；getInput/getInputSize/getOutput→`grid* output*` | T-510 |
| PlayerInventory | `PlayerInventory`【已有】 | `'player'` | —（本地背包） | getBoots/Chestplate/Helmet/Leggings→`armor*` 槽位命名 `armor[EquipmentSlots]` ⚠ 或独立属性、getOffhand→`offhand*`【已有】、isInHotbar→`isInHotbar(slot)`【12 号 §2.4 已设计】 | T-510 |
| ContainerInventory | ``ContainerInventoryEntity`` | `'chest'` | `ChestMenu` | findFreeContainerSlot→`freeSlot()`【12 号已设计 region 语义】 | T-510 |
| CraftingInventory | ``CraftingInventoryEntity`` | `'crafting'` | `CraftingMenu` | getInput→`grid*` | T-510 |
| FurnaceInventory | ``FurnaceInventoryEntity`` | `'furnace'` | `FurnaceMenu`（BlastFurnaceMenu/SmokerMenu 同成员，kind 区分） | canUseAsFuel→`isFuel(stack)`、getFuel/getInput/getSmeltedItem→`fuel* input* output*`、getFuelValues→`fuelValue(stack)`、getPropertyDelegate→D（内部）、getRemainingFuelTime/getTotalFuelTime→`fuelTime* fuelTimeTotal*`、getRemainingSmeltingTime/getTotalSmeltingTime→`smeltingTime* smeltingTimeTotal*`（Mojang AbstractFurnaceBlockEntity 字段名 ⚠核对）、getSmeltingProgress→`smeltingProgress*`、isBurning→`isLit*`、isSmeltable→`input !== undefined` | T-510 |
| AnvilInventory | ``AnvilInventoryEntity`` | `'anvil'` | `AnvilMenu` | getItemRepairCost→`repairItemCost*` ⚠、getLeftInput/getRightInput→`left* right*`（Mojang `inputSlots` 语义）、getLevelCost→`cost*`、getMaximumLevelCost→`costLimit*`（40）、getName/setName→`itemName*`（`setItemName` 动作）、getOutput→`output*` | T-510 |
| EnchantInventory | ``EnchantInventoryEntity`` | `'enchanting'` | `EnchantmentMenu` | doEnchant→`enchant(id?)`? ⚠（客户端点击槽位动作）、getEnchantments/getEnchantmentIds/getEnchantmentLevels→`enchantments*`（{id, level}[]）、getEnchantmentHelpers→D（内部）、getItemToEnchant→`input*`、getLapis→`lapis*`、getRequiredLevels→`costs*: number[]`（Mojang `cost[]`） | T-510 |
| VillagerInventory | ``VillagerInventoryEntity`` | `'villager'` | `MerchantMenu` | canRefreshTrades→`canRestock*` ⚠、getExperience→`tradesProgress*` ⚠（MerchantOffers progress）、getLevelProgress→`level*`、getMerchantRewardedExperience→`rewardExp*` ⚠、getTrades→`trades*: TradeOffer[]`（与 §3 AbstractVillager.trades 同一 TradeOffer 类）、isLeveled→`level > 0`、selectTrade→`selectTrade(index)`（Mojang `clickMenuButton` 路径） | T-510 |
| BeaconInventory | ``BeaconInventoryEntity`` | `'beacon'` | `BeaconMenu` | applyEffects→`applyEffects()`? ⚠（clickMenuButton 路径）、getFirstEffect/getSecondEffect→`primaryEffect* secondaryEffect*`、getLevel→`levels*`? ⚠（beacon tier）、selectFirstEffect/selectSecondEffect→`selectPrimary(id) selectSecondary(id)`（Mojang `selectEffect`? ⚠） | T-510 |
| BrewingStandInventory | ``BrewingStandInventoryEntity`` | `'brewing'` | `BrewingStandMenu` | getBrewTime→`brewTime*`、getFuel/getFuelCount/getMaxFuelUses→`fuel*`（`fuel` slot + `brewingStandData` 拆解 ⚠）、getIngredient→`ingredient*`、getPotions/getFirst/Second/ThirdPotion→`slots*`（三药槽枚举）、getRemainingTicks→`brewTime*` 同源、isBrewablePotion→`isBrewable(id)`? ⚠、isValidIngredient/isValidRecipe→`isValidIngredient(stack)`（Mojang PotionBrewing 静态 ⚠）、previewPotion(s)→D 评估 ⚠ | T-510 |
| CartographyInventory | ``CartographyInventoryEntity`` | `'cartography'` | `CartographyTableMenu` | getMapItem/getMaterial→`map* material*`、getOutput→`output*` | T-510 |
| GrindStoneInventory | `GrindstoneInventory`（拼写对齐 Mojang） | `'grindstone'` | `GrindstoneMenu` | getBottomInput/getTopInput→`bottom* top*`（Mojang `inputSlots`）、getExperience/simulateXp→`xp* xpEstimate()` ⚠（GrindstoneMenu::getExperienceAmount）、getOutput→`output*` | T-510 |
| HorseInventory | ``HorseInventoryEntity`` | `'horse'` | `HorseInventoryMenu` | canBeSaddled→`isSaddleable*`（与 §3 AbstractHorse 同源）、getArmor/getSaddle→`armor* saddle*`、getHorse→`horse*: AbstractHorse` 句柄、getHorseInventory→自引用 D、getInventorySize→`size*`、hasArmorSlot→`canWearArmor*`、hasChest→`horse.hasChest`、isSaddled→`horse.isSaddled`、horse→`horse*` | T-510 |
| LoomInventory | ``LoomInventoryEntity`` | `'loom'` | `LoomMenu` | getPatternsFor/listAvailablePatterns→`patterns*`（pattern ids）、selectPattern/Id/Name→`selectPattern(id)`（Mojang `clickMenuButton` 路径 ⚠） | T-510 |
| SmithingInventory | ``SmithingInventoryEntity`` | `'smithing'` | `SmithingMenu` | getLeftInput/getRightInput→`template* input*`? ⚠（smithing template 槽并入 ⚠核对槽位语义）、getOutput→`output*` | T-510 |
| StoneCutterInventory | `StonecutterInventory`（拼写对齐 Mojang） | `'stonecutter'` | `StonecutterMenu` | canCraft→`output !== undefined`、getAvailableRecipeCount→`recipeCount*`? ⚠、getOutput→`output*`、getRecipes→`recipes*`（显示配方 id 列表）、getSelectedRecipeIndex→`selectedRecipeIndex*`? ⚠、selectRecipe→`selectRecipe(index)`（clickMenuButton） | T-510 |
| CreativeInventory | ``CreativeInventoryEntity`` | `'creative'` | `CreativeModeInventoryScreen`（Screen 非 Menu ⚠专项） | destroyAllItems/destroyHeldItem→D 评估（客户端伪造+服务端拒绝风险 ⚠）、getBoots…getOffhand→PlayerInventory 复用 A、getSavedHotbar/restoreHotbar/saveHotbar→`hotbarSavings` 子面 ⚠、getShownItems→`items*`、getTabNames/getTabTexts→`tabs*`、isInHotbar→A、scroll/scrollTo/search/selectHotbar/selectInventory/selectSearch/selectTab→`selectTab(id) scroll(amount) search(text)` 动作族、setCursorStack/setStack→`setCursor(stack) setSlot(stack)`? ⚠ | T-510（评估后可能缩水） |

## 5. 模块缺口映射总表

> 覆盖 missing-apis §1 / §2 / §5 / §7 的全部条目。列：JsMacros 源（§ 缺口成员）→ NeoMacros 成员（模块前缀）→ Mojang 依据 → 归宿 → 批次（首批 = T-502~T-506；后续批次号见 §7）。
> 同一 Mojang 依据支撑多条 JsMacros 成员时合并为一行。

### 5.1 `world`（§1 World 25 项 + §5 Chunk/Scanner）

| JsMacros 源 | NeoMacros | Mojang 依据 | 归宿 | 批次 |
|---|---|---|---|---|
| `getPlayers` `getPlayerEntry` | `world.tabList` 属性 → `TabList {header*, footer*, entries: [{uuid*, name*, ping*, gamemode*, displayName*}]}` | `PlayerTabOverlay`? ⚠ / `ClientPacketListener.getOnlinePlayers()` → `PlayerInfo` | B + C（TabList 数据） | T-502 |
| `getEntitiesInternal` | `entity.inBox` / `entity.inRadius` | — | A | — |
| `findBlocksMatching` `getWorldScanner`（+§5 WorldScannerBuilder 36 项） | `world.findBlocks({types?, states?, center?, range?, limit?}): Block[]`（range 上限 + 主线程分片，options 对象替代 builder 链） | `Level.getBlockState` + `BlockPos.betweenClosed` | B | T-511 |
| `getScoreboards` | `world.scoreboards` 属性 → 只读谱系（objectives/scores）| `ClientLevel.getScoreboard()` → `ClientScoreboard` | B | T-502 |
| `getWorldIdentifier` | `world.levelName` 属性 | `LevelData.getLevelName()` ⚠ | B | T-502 |
| `getRespawnPos` | `player.self.respawnPos` | `Player.getRespawnPosition()` | B | T-502 |
| `getMoonPhase` | `world.moonPhase` 属性（0-7） | `Level.getMoonPhase()` | B | T-502 |
| `getSkyLight` `getBlockLight` | `world.lightAt(pos): {sky, block}`（12 号 §4.4 已设计） | `Level.getBrightness(LightLayer, pos)` | B | T-502 |
| `playSound`（+`playSoundFile`） | `world.playSound(id, pos?, {volume?, pitch?}?)`；文件路径入参（.ogg）走自定义 `SoundInstance` ⚠ | `SoundManager.play` / `Level.playLocalSound` | B（12 号已设计） | T-502 |
| `spawnParticle` | `world.spawnParticle(id, pos, {count?, offset?, speed?}?)`（12 号 §4.4 已设计，第二轮定名 spawnParticle） | `Level.addParticle` | B | T-502 |
| `getBossBars` | `world.bossbars` 属性 → `BossBar[]`（C 新增 `BossBar` DATA 类：`uuid* name* progress* color*`，12 号 §2.4 已列） | `Gui` → `BossHealthOverlay.getBossBars()` ⚠ | B + C | T-502 |
| `getCurrentServerAddress` | `client.serverInfo`（§5.4） | `Minecraft.getCurrentServer()` | A→合并 | T-503 |
| `getServerTPS` `getServerInstantTPS` `getServer1M/5M/15MAverageTPS` | `world.tps` 属性 → `{instant, m1, m5, m15}`（time-update 节奏采样，客户端近似，文档标注） | ClientboundTimeUpdatePacket 间隔采样（无原生 Mojang API） | B | T-502 |
| `getTabListHeader/Footer` | `world.tabList.header/footer`（并入上行 TabList） | `PlayerTabOverlay` ⚠ | B | T-502 |
| `getRaw` | —（穿透层配方） | `Minecraft.getInstance().level` | D | — |
| §5 ChunkHelper 20 项 | `Chunk` HANDLE 类（`chunkX* chunkZ* minBlock* biome(pos) heightmap(name) heightAt(x,z) entities() blockEntities() contains(pos) inhabitedTime* maxY* minY*` 族）+ `world.chunkAt(pos)` | `LevelChunk`（`LevelReader.getChunk`） | C | T-511 |
| §7 `World.getBiome` | `world.biomeAt(pos)`【已有】；no-world 时由抛错改返回 `undefined`（缺席语义对齐） | — | A（语义微调） | T-511 |

### 5.2 `player`（§1 Player 21 项 + §2 ClientPlayer 基类 + §5 Abilities/InteractionManager）

| JsMacros 源 | NeoMacros | Mojang 依据 | 归宿 | 批次 |
|---|---|---|---|---|
| `setGameMode` | — | 客户端无写权 | D（`chat.say('/gamemode …')` 等价） | — |
| `getStatistics`（+§5 StatsHelper 19 项） | `player.self.statistics` 属性 → `Record<statId, number>`；`player.self.stat(statId)` 带参查询；格式化族由脚本侧组合 | `Player.getStats()` → `StatsCounter`（`getValue`） | B | T-504 / 扩全 T-515 |
| `getReach` | `player.self.blockInteractionRange` / `entityInteractionRange` 属性 | `Player.blockInteractionRange()` / `entityInteractionRange()` | B | T-504 |
| `takeScreenshot` | `client.grabScreenshot(): Promise<Image>`（C 新增 `Image` DATA 类 wraps `NativeImage`；`fs.writeBytes` 可落盘） | `Screenshot.grab` ⚠（26.x 异步管线 ⚠核对） | B + C | T-504 |
| `takePanorama` | `client.grabPanorama()` ⚠（相机逐面捕获专项） | 自组装（无原生 API）⚠ | B | T-520 |
| `writeSign` | `player.self.writeSign(pos, lines)`（打开符牌编辑屏提交；依赖 screen 管线） | `SignEditScreen` / `SignApplicator` ⚠ | B | T-521（P3'） |
| `createPlayerInput` `createPlayerInputsFromCsv/Json` `addInput(s)` `clearInputs` `getCurrentPlayerInput` `moveForward/Backward/StrafeLeft/StrafeRight` | `input.movement({forward?, backward?, left?, right?, jump?, sneak?, sprint?}): Subscription`（每 tick 覆盖 `KeyboardInput` 冲量）+ `input.movementState` 属性 → `{forward, left, jump…}` 读面；CSV/JSON 构造由脚本侧自行组装 | `KeyboardInput`（`forwardImpulse` `leftImpulse`）、`KeyMapping` | B | T-504 |
| `setDrawPredictions` `predictInput(s)` | — | JsMacros 输入覆盖器专属显示 | D（默认；W3 实施时复核 ⚠） | — |
| `isBreakingBlock` | `player.self.isBreakingBlock` 属性 | `MultiPlayerGameMode.isDestroying()` | B | T-504 |
| §2 ClientPlayer 写面（addPos/setPos/setVelocity/addVelocity/lookAt/tryLookAt/turnLeft/Right/Back） | `LocalPlayer` 动作族：`teleport(pos)`? ⚠（客户端本地瞬移）`velocity*` 读写 `look(yaw, pitch)`（12 号 P2 已列）`lookAt(target)` | `LocalPlayer` / `Entity.setPos` / `setDeltaMovement` | B | T-512 |
| §2 interact/interactItem/setLongInteract/setLongAttack | `LocalPlayer.interactItem()`（12 号 P2 遗留已列 interactItem）+ 长按族 ⚠ | `MultiPlayerGameMode.useItem` / `attack` | B | T-512 |
| §2 getAdvancementManager | `client.advancements` 只读面 ⚠ | `ClientAdvancements` | B | T-515 |
| §2 getItemCooldownRemainingTicks 族 | `player.self.itemCooldown(id)` / `itemCooldowns` 属性 | `ItemCooldowns`（`getCooldownPercent`） | B | T-512 |
| §2 mc 属性 | `.raw`（java 闸） | `Minecraft` | A（穿透约定） | — |
| §5 PlayerAbilitiesHelper 11 项 | `player.self.abilities` 属性 → `Abilities` HANDLE：`invulnerable* flying* mayFly* instabuild* flySpeed* walkingSpeed* mayBuild*` + `setFlying(v) setFlySpeed(v) setWalkingSpeed(v)`（写=客户端态，文档标注仅本地） | `Player.getAbilities()` → `Abilities` | B | T-512 |
| §5 InteractionManagerHelper 25 项 | `LocalPlayer` 交互子系统：`breakBlock(pos) cancelBreakBlock() holdInteract()` 族 + target override 族 ⚠（评估后可能 D 缩水） | `MultiPlayerGameMode`（`startDestroyBlock` `continueDestroyBlock` `stopDestroyBlock` `useItemOn`） | B | T-512 |
| §7 `Player.getInteractionManager` `Player.openInventory` | `player.inventory`（容器优先，默认回退玩家背包句柄，第六轮更名实施）+ 自背包开屏：`player.openInventory()` → `Minecraft.setScreen(new InventoryScreen(player))` ⚠ | `InventoryScreen` | B | T-510（改名部分已实施） |
| §7 `Player.getInteractionManager`（其余） | — | 交互动作面即其替代 | D | — |

### 5.3 `chat`（§1 Chat 9 项 + §5 Text/CommandManager/ChatHistory）

| JsMacros 源 | NeoMacros | Mojang 依据 | 归宿 | 批次 |
|---|---|---|---|---|
| `logInternal` `sayInternal` | `chat.log` / `chat.say` | — | A | — |
| `open` | `chat.open()` | `Minecraft.setScreen(new ChatScreen(""))` | B | T-521（P3'，随 screen 首件；实现成本极低可搭车） |
| `title` | `chat.title(title?, subtitle?, {fadeIn?, stay?, fadeOut?}?)`（12 号 §4.3 已设计） | `Gui.setTitles` / `setTimes` / `clear` ⚠ | B | T-504 |
| `toast` | `chat.toast({title?, message?, type?})` | `ToastComponent.addToast` + `SystemToast` ⚠ | B | T-504 |
| `getLogger` | — | — | D | — |
| `createCommandBuilder` `unregisterCommand` `reRegisterCommand`（+§5 CommandManager/CommandBuilder 50 项） | `command.register({name, args: command.arg.*})` / `command.unregister`（T-423 类型化 DSL 已有）；自动补全族（`getArgumentAutocompleteOptions`）⚠ 评估 | brigadier（不暴露类型） | A | — |
| §5 TextHelper 4 项 | `chat.clean(s)`（12 号已设计）；`chat.width(str)`（`Font.width`）；`chat.wrap(str, width)`（`Font.split` ⚠ 返回行数组）；`STRIP_FORMATTING_PATTERN`/`mc` 内部不暴露 | `Font` | B | T-504（width）/ T-514（wrap、Text 面全量） |
| §5 TextBuilder 11 项 | `TextBuilder`（12 号 §4.3 已设计 color/bold/…/hover/clickCommand/clickUrl/append/build）落地（API2 遗留顺延）+ 补 `withStyle→style({})` 合并形、hover 的 showItem/showEntity 变体 ⚠ | `Component` / `MutableComponent` / `ClickEvent` / `HoverEvent` | B | T-514 |
| §7 `Chat.getTextWidth` `TextBuilder.withColor` | 随上行落地消解 | — | A | T-514 |
| §5 ChatHistoryManager 13 项 | `chat.history` 属性 → `{received: Text[], sent: string[], input: string[]}` 只读（`clear/remove/insert/refresh` mutate 族随 screen/overlay 专项 ⚠） | `ChatComponent` / `ChatLog` / `CommandHistory`? ⚠ | B | T-514（读） / T-521（写） |

### 5.4 `client`（§1 Client 23 项 + §5 Registry/Options/Mods/ServerInfo）

| JsMacros 源 | NeoMacros | Mojang 依据 | 归宿 | 批次 |
|---|---|---|---|---|
| `connect` | `client.connect(url): Promise<void>` | `ConnectScreen.startConnecting` ⚠ | B | T-503 |
| `disconnect` | `client.disconnect()` | `Minecraft.disconnect()` / `clearClientLevel`? ⚠ | B | T-503 |
| `loadWorld` | `client.loadWorld(name): Promise<void>` | `Minecraft.loadLevel(LevelStorageAccess)` ⚠ | B | T-503 |
| `shutdown` `exitGamePeacefully` `exitGameForcefully` | `client.shutdown({force?}?)`（force = `System.exit`，文档风险标注） | `Minecraft.stop()` | B | T-503 |
| `ping` `pingAsync` `cancelAllPings` | `client.ping()`（当前服务器延迟）/ `client.ping(url): Promise<PingResult>`（状态 ping 专项） | `Connection` 延迟采样 / 状态协议自组装 ⚠ | B / 专项 | T-503（前者） / T-520（后者） |
| `getLoadedMods` `isModLoaded` `getMod`（+§5 ModContainerHelper 7 项） | `client.mods` 属性 → `[{id, name, version, authors?, description?, env?}]`；`client.isModLoaded(id)`；`client.mod(id)` | `FabricLoader.getAllMods()` / `getModContainer` | B | T-503 |
| `getModLoader` | — | 单一加载器 | D | — |
| `isDevEnv` | `client.isDevEnv` 属性 | `FabricLoader.isDevelopmentEnvironment()` | B | T-503 |
| `grabMouse` | `client.grabMouse()` / `client.releaseMouse()`（对称补） | `MouseHandler.grabMouse/releaseMouse` | B | T-503 |
| `sendPacket` `receivePacket` `createPacketByteBuffer`（+§5 PacketByteBufferHelper ~130 项） | `packet:receive` / `packet:send` 事件 + `PacketBuffer` 类（12 号 §5.3 P2 已排专项） | `Connection.send` / channel read ⚠ | C（专项） | T-517 |
| `getClipboard` `setClipboard`（+§5 Utils 同名） | `client.clipboard` 属性（可写） | `KeyboardHandler.getClipboard/setClipboard` | B | T-503 |
| `getGameOptions`（+§5 OptionsHelper 45 项） | `client.options` 属性 → `Options` HANDLE（首批：`fov* guiScale* renderDistance* gamma*` 只读小集；全谱 T-515） | `Minecraft.options` → `Options` | B | T-503 / T-515 |
| `getRegisteredBlocks` `getRegisteredItems`（+§5 RegistryHelper 40 项） | `client.registry` 子面：`ids('block'|'item'|…)` 只读数组 + `lookup(kind, id)` | `BuiltInRegistries` | B | T-503（小集）/ T-515（全谱） |
| §5 ServerInfoHelper 13 项 | `client.serverInfo` 属性 → `{name*, address*, label*, version*, protocolVersion*, playerCount*, players*, ping*, isLocal*, icon?}`（ping/players 随状态 ping 专项补全） | `ServerData` / `ServerStatus` ⚠ | B | T-503 / T-520 |
| §7 `Hud.isContainer` | `player.screen.container !== undefined`（第六轮已实施：`player.screen` 升级为 Screen 句柄族，`InventoryScreen.container`/`ContainerScreen.container` 均落地） | — | A（已实施） | T-505（读面部分） |

### 5.5 `hud` / `render` / `screen`（§1 Hud 11 项 + §5 Draw2D/Draw3D/CustomImage/ScriptScreen/IScreen）

| JsMacros 源 | NeoMacros | Mojang 依据 | 归宿 | 批次 |
|---|---|---|---|---|
| `createScreen` `openScreen`（+§5 ScriptScreen 10 项 / IScreen 36 项） | `screen.build(spec)`（12 号 §4.16 P3 方向，立项前单独评审） | `Screen` 体系 | C（P3 专项） | T-521 |
| `getOpenScreen` `getOpenScreenName` | `player.screen` 属性 → **Screen 句柄族**（第六轮已实施：基类 `Screen{title*, kind*, name*, close()}` + `XxxScreen` 特化类族；容器屏 `.container`；聊天/告示牌/书 `.text`；对话框 `.dialog`） | `Minecraft.screen` | B + C（读面已实施） | T-505 |
| `createTexture` `getRegisteredTextures`（+§5 CustomImage ~50 项） | 纹理专项：`hud.texture(name, spec)` + `Image` 2D 绘制族（Canvas-like API 评估，可能缩水） | `DynamicTexture` / `TextureManager` ⚠ | C（专项） | T-518 |
| `getScaleFactor` | `client.window.guiScale`（§5.4） | `Window.getGuiScale`? ⚠ | A→合并 | T-503 |
| `getMouseX` `getMouseY` | `client.mouse` 属性 → `{x*, y*}` | `MouseHandler.xpos/ypos` | B | T-503 |
| `getWindowWidth/Height` | `client.window` 属性 → `{width*, height*, scaledWidth*, scaledHeight*}` | `Window.getWidth/getHeight` ⚠ | B | T-503 |
| §5 Draw2D 38 项 | `hud.layer(id)` 模型（12 号 §4.15）扩展：`line(id, {…})`、`image(id, {…})` ⚠、`item(id, stack, {…})` ⚠、z-index 排序、nested Draw2D | `GuiGraphics` 管线 | B | T-516 |
| §5 Draw3D 15 项 | `render.layer(id)` 模型扩展：`traceLine(entity)`（每帧重算 ⚠）、`surface(顶点族)` ⚠、`boxBuilder` 形态→options 对象 | 26.x gizmo 管线 | B | T-516 |
| §7 `Draw3D.addPoint` | 现状 wireframe cube 近似已存在 | — | A | — |

### 5.6 `input`（§1 KeyBind 4 项 + Player 移动族）

| JsMacros 源 | NeoMacros | Mojang 依据 | 归宿 | 批次 |
|---|---|---|---|---|
| `getKeyBindings` | `input.keybinds` 属性 → `[{name*, key*, category*}]` | `Options.keyMappings` → `KeyMapping.getAll()` ⚠ | B | T-504 |
| `setKeyBind` | `input.setBind(bindName, keyName)`（写后 `Options.save` ⚠） | `KeyMapping.setKey`? ⚠ | B | T-504 |
| `getKeyCode` | `input.keybinds` 条目 `key` 字段 | `KeyMapping` | A | T-504 |
| `getPressedKeys` | `input.pressedKeys` 属性 → `string[]` | `KeyMapping.isDown` 扫描 / `InputConstants` | B | T-504 |
| Player 移动族 | `input.movement(spec)` / `input.movementState`（§5.2） | `KeyboardInput` | B | T-504 |

### 5.7 `fs`（§1 FS 4 项 + §5 FileHandler）

| JsMacros 源 | NeoMacros | Mojang 依据 | 归宿 | 批次 |
|---|---|---|---|---|
| `toRelativePath` | `fs.toRelative(path): string`（相对游戏目录；越界抛错沿现状语义） | `Path.relativize` | B | T-505 |
| `toRawFile` `toRawPath` `getRawAttributes`（+§5 FileHandler `getFile/streamBytes/charset/f`） | `fs.open(path): FileHandle`（HANDLE）：`readBytes(): Promise<Uint8Array>` 流读 + `attributes` 属性（`{size, mtimeMs, isDir}`）+ `raw`（java 闸：`Path`/`File` 原生句柄） | `java.nio.file` | B | T-505 |

### 5.8 `net`（§1 Request 2 项 + §5 HTTPRequest）

| JsMacros 源 | NeoMacros | Mojang 依据 | 归宿 | 批次 |
|---|---|---|---|---|
| `createWS` `createWS2` | `net.connect(url): Promise<Ws>`（12 号 §4.12 已设计：`send close on(name, fn)`，name: open/message/close/error） | `java.net.http.HttpClient.newWebSocketBuilder` | B | T-505 |
| §5 HTTPRequest 4 属性 + `Response.raw` | fetch `{timeoutMs?}` 选项 ⚠ + `Response.headers` 数组化补全 | `HttpClient` / `HttpResponse` | B | T-514 |

### 5.9 `java` / `reflect` / `thread`（§1 Reflection/JavaUtils/JavaWrapper）

| JsMacros 源 | NeoMacros | 归宿 | 批次 |
|---|---|---|---|
| `FReflection.compile 族 8 项` | — | E（§2.1） | — |
| `loadMappingHelper` `loadCurrentMappingHelper` | — | D（内建翻译） | — |
| `getWrappedClass` `wrapInstace` | `java.wrap(obj)`（12 号 §4.9 设计成员；如未实施随本批补） | A/B | T-506 |
| `createClassProxyBuilder` | `java.proxy(interfaces, handlers)`【已有】 | A | — |
| `getReflect` | `neo.reflect`【已有】 | A | — |
| `FJavaUtils.getHelperFromRaw` | `java.wrap(obj)` | A | T-506 |
| `FJavaUtils` 集合构造族 | `java.toList/fromList/toArray`【已有】 | A | — |
| `FWrapper` 3 项 | — | D（§2.2） | — |

### 5.10 `types`（§2 实体基类 + §5 数据类既有类型扩充）

| JsMacros 源 | NeoMacros | Mojang 依据 | 归宿 | 批次 |
|---|---|---|---|---|
| §2 EntityHelper 14 项 | `entity.chunk`（随 `Chunk` 类）、`speed*`、`nbt` 只读 ⚠（JSON 化评估）、`glowing*` 读写 + `glowColor*` 族、`setCustomName(name)` `customName*` `customNameVisible*` | `Entity.getSpeed` / `saveWithoutId` ⚠ / `setGlowingTag` ⚠ | B | T-512 / T-511（chunk） |
| §2 LivingEntityHelper 22 项 | `absorption` `armorValue` `effects` `hasEffect(id)`（API2-F2 遗留已列，首批顺延后落地）；`maxAir*` `useItemRemainingTicks*` `noGravity*` `baby*` `undead*` ⚠ `breathesUnderwater*` ⚠ `canBeAffected(id)` `hasLineOfSight(target)` `canAttack(target)` ⚠ `armor*[4]`（12 号 P2 已列）`bowPull*` ⚠ `defaultMaxHealth*` ⚠ `tags*` `noDrag` ⚠ `isPartOfGame` ⚠ | `LivingEntity` getter 族 | B | T-512 |
| §2 PlayerEntityHelper 7 项 | `abilities`（§5.2）、`attackCooldown*` 族、`fishingHook*`、`score*`、`xp.nextLevelCost*`? ⚠、`sleepingLongEnough*` ⚠ | `Player` getter 族 | B | T-512 |
| §2 ItemEntityHelper | `ItemEntity.stack`【已有】 | — | A | — |
| `Pos2D.ZERO` | `Pos2D` 静态常量 `ZERO`（guest 类静态成员） | — | B | T-512 |
| `Pos3D.toMojangDoubleVector` `toRawBlockPos` | —（`floor()` 已有；穿透层 `java.new('net.minecraft.world.phys.Vec3', x, y, z)` 配方） | `Vec3` | D | — |
| `Vec2D.compareTo` | `Pos2D.compare(v)`? ⚠（语义=先 x 后 y 的字典序） | — | B | T-512 |
| §5 BlockDataHelper 10 项 | `Block.state`【已有】；`blockEntity` 子面 ⚠（nbt/类型）；`getRawBlock/State/Entity` + `b/bp/e/mc` → `.raw` 穿透；`getBlockHelper` 工厂不设 | — | A/B | T-513 |
| §5 BlockStateHelper 21 项 | `BlockState` 扩充：`allowsSpawning*` ⚠ `blocksMotion*` ⚠ `emitsRedstonePower*`（`isSignalSource`）`exceedsCube*` ⚠ `fluidState*` ⚠ `hardness*`（`destroySpeed`）`luminance*`（`lightEmission`）`pistonBehaviour*` ⚠ `hasBlockEntity*` ⚠ `hasComparatorOutput*` ⚠ `hasRandomTicks*` ⚠ `isBurnable*` ⚠ `opaque*`（`canOcclude`）`replaceable*`（`canBeReplaced`）`solid*` ⚠ `toolRequired*`（`requiresCorrectToolForDrops`）`suffocating*` ⚠；`getBlock`→`Block` 句柄族/`state.is(id)`【已有】；`create` 工厂 D；`getUniversal` ⚠ | `BlockStateBase` | B | T-513 |
| §5 ItemStackHelper 34 项 | `ItemStack` 扩充：`attackDamage*` `cooldownProgress*` `maxCount*`（`maxStackSize`）`repairCost*` `tags*` ⚠ `item*`（→ `Item` 句柄）`isDamageable()` `isEnchantable()` `isEnchanted()` `isEdible()` `isOnCooldown()` `isCorrectToolForDrops(block)` `isUnbreakable*` `wearableSlot*`? ⚠ `possibleEnchantments` ⚠ `setDamage(n)`；`creative`/`creativeTab` ⚠ D 评估；destroy/place restrictions（legacy NBT）D 评估；`nbt` → 穿透配方；`LORE_STYLE`/`mc` 内部 | `ItemStack` / `Item` | B | T-513 |
| §5 HitResultHelper 3 项 | `RayHit.block/entity`【已有内嵌】；`resolve` 工厂不设 | — | A | — |

## 6. 事件增补表（§6）

### 6.1 不支持事件名 → NeoMacros 事件映射

| JsMacros 事件 | NeoMacros 事件 | 12 号 §5.3 状态 | 批次 |
|---|---|---|---|
| `RecvPacket` / `SendPacket` | `packet:receive` / `packet:send`（send 可取消） | 已设计（P2 专项，含 PacketBuffer） | T-517 |
| `Title` | `chat:title`（`title?, subtitle?`） | 已设计 P2 | T-519 |
| `SignEdit` | `sign:edit`（`pos, lines`）⚠（tick 轮询或屏幕监听） | 新增 | T-519 |
| `NameChange` | `entity:customName`（`uuid, name`）⚠ | 新增 | T-519 |
| `Riding` | `player:riding`（`vehicle: EntitySnapshot \| undefined`） | 已设计 P2 | T-519 |
| `ArmorChange` | `player:armor`（`armor: ItemStack[4]`） | 已设计 P2 | T-519 |
| `OpenContainer` | `inventory:open`（`title, kind, size, containerId`；E 背包 kind=`player`） | 第六轮 2026-11-08 已实施（tick 差分）；**T-524 2026-11-11 增可取消**：取消=立即关闭容器（服务端权威，最多 1 tick 可见；非 JsMacros 的屏幕回滚） | A（已实施+可取消） |
| `CloseContainer` | `inventory:close`（`kind, title`） | 第六轮新增·已实施 | A（已实施） |
| `ContainerUpdate` / `SlotUpdate` | `inventory:update`（`mode('slot'\|'all'\|'held'), containerId, kind, slot, oldStack?, newStack?`） | 第六轮已实施；**T-524 补 `oldStack/newStack` + `held` 光标堆**（`handleContainerSetSlot/Content/SetCursorItem` HEAD 预写） | A（已实施+字段补全） |
| `ClickSlot` | `inventory:click`（`containerId, kind, slot, button, mode, shift`，可取消） | 第六轮 TAIL 通知；**T-524 改为 HEAD 可取消 C2S**（取消后点击包不发送、槽位不变），补 `mode` 动作名 | A（已实施+可取消） |
| `DropSlot` | `inventory:dropSlot`（`kind, slot, all`，可取消） | **T-524 新实施**：菜单 THROW 与热栏 Q 两路包级拦截（`ServerboundPlayerActionPacket`）；click 取消则 drop 不再触发 | A（已实施） |
| `ItemDamage` | `item:damage`（`slot(main/offhand/armor), item, damage`） | **T-524 新实施**：tick 差分主手+护甲；`stackDataOf` 补真实 damage/maxDamage | A（已实施，副手暂缺） |
| `ItemPickup` | `item:pickup`（`item, amount`） | **T-524 新实施**：`handleTakeItemEntity` HEAD，按本地玩家过滤 | A（已实施） |
| `ResourcePackLoaded` | `game:resourcePack`（`id?`） | 已设计 P2 | T-519 |
| `ProfileLoad` / `WrappedScript` / `Service` / `CommandContext` | — | D（§2.2） | — |

### 6.2 已桥接事件缺字段补齐

| 事件 | 补齐字段 | Mojang 依据 | 批次 |
|---|---|---|---|
| `world:chunkLoad` | `full*` | `LevelChunk.isFull`? ⚠ | T-519 |
| `player:damage` / `entity:damage` | `source: EntitySnapshot \| undefined`（攻击者/伤害源） | `DamageSource` 拆解（`getDirectEntity`/`getEntity`） | T-519 |
| `player:heal` / `entity:heal` | `source` | 同上 | T-519 |
| `world:leave` | `reason?` | 断开原因 ⚠ | T-519 |
| `player:health` | `change*`（差量可推，核对后定为 A 或小批） | — | T-519 |
| `player:exp` | `previous {level, progress, total}`（现状 payload 有 change，补 prev 三元） | — | T-519 |
| `entity:unload` | `reason*`（`'despawn' \| 'unloaded' \| 'chunk'`）⚠ | 卸载路径判定 | T-519 |
| `world:join` | `address*` | `ServerData.ip` | T-519 |
| `player:interactBlock` / `player:interactEntity` | `hand*`（`'main' \| 'off'`）、`result*` ⚠ | 交互事件源签名扩展 | T-519 |
| `key:press` | `modifiers`【已有】；`parse` 工具→脚本侧 A；`wasNullOnDown` → D ⚠ | — | A / D |
| `game:start` | `playerName*`（`player.self.name` 可推，核对后定） | — | T-519 |
| `player:death` | `respawn` 方法 → A（`player.self.respawn()`，12 号 P2 动作面）；`inventory` 快照 ⚠ | — | A / T-519 |
| `inventory:open` / `inventory:update` | `screen`（MC 类名）→ B 已实施；完整句柄等价面 `player.screen` | `AbstractContainerScreen` 实例按冻结载荷规则（12 §5.1）转类名 | G-013 |
| `inventory:click` / `inventory:dropSlot` / `inventory:open` / `inventory:update` 的 `inventory` / `getInventory` | **D**（不进 payload） | 载荷冻结纯对象禁活句柄（`@ApiEvent` "never live game references" / 12 §5.1）；菜单身份已由 `kind`/`containerId`（open 另有 `title`/`size`）承载，槽内容等价面 = `player.inventory`（12 §5.3 已拍板"容器经 player.inventory 取句柄"） | G-013 |
| `EventSlotUpdate.type`（→ `inventory:update`） | **D**（保留键） | `type` 是 BaseEvent 保留 payload 键（`ev.type` = 事件名，`EventDescriptor.RESERVED_PAYLOAD_KEYS` 拒绝同名组件）；来源分类由 `mode`（`slot`/`all`/`held`）+ `containerId`/`kind` 承载，JsMacros `UNKNOWN` 分支客户端不建模 | G-013 |

### 6.3 手动补充受限成员

维持现状（§2.2 D 清单末行），不新增动作。

## 7. 全量 backlog 分期与任务编号

| 批次 | 内容 | 任务 |
|---|---|---|
| **首批（本计划）** | missing-apis §1 全局库全量（13 库 ~138 项；D/E 清单除外；packet/screen/纹理/panorama/状态 ping/writeSign 依赖专项顺延标注） | T-502~T-508 |
| **P2' 之一** | §3 实体物种子类树全量（75 壳 → §3.2 映射表） | T-509 |
| **P2' 之二** | §4 容器特化树全量（18 类 + TradeOffer 类 + villager trades） | T-510 |
| **P2' 之三** | `Chunk` 类 + `world.findBlocks` 扫描 DSL + `biomeAt` 语义微调（§5 ChunkHelper/WorldScannerBuilder 56 项） | T-511 |
| **P2' 之四** | 实体/生物通用成员批（§2 基类 + §5 Abilities/InteractionManager/ItemStack/BlockState 小批，T-512/T-513） | T-512/T-513 |
| **P2' 之五** | Text/TextBuilder 全量 + chat.width/wrap/history 读 + fetch 补全（含 API2-F2 遗留合并） | T-514 |
| **P2' 之六** | client 扩全：`client.registry` 全谱 + `client.options` 全谱 + advancements + serverInfo 状态 ping | T-515 |
| **P2' 之七** | Draw2D 元素扩展（line/image/item/z-index）+ Draw3D surface/traceLine | T-516 |
| **P2' 之八** | packet 专项（`packet:receive/send` + `PacketBuffer` + `client.sendPacket/receivePacket`） | T-517 |
| **P2' 之九** | 纹理专项（`hud.texture` + `Image` 绘制族评估） | T-518 |
| **P2' 之十** | 事件字段补齐批（§6.1/§6.2 清单） | T-519 |
| **P2' 之十一** | 状态 ping / panorama / movement 依赖复核专项 | T-520 |
| **P3'** | `screen.build` 自建 GUI、`chat.open`、`writeSign`、chat history mutate、CreativeInventory、nested Draw2D | T-521+ |

每批任务登记进 `neomacros/plan/TASKS.md`（本地看板），按 09 号执行协议（认领 → in-progress → 报告 `plan/reports/<ID>.md`）流转。

## 8. 首批（全局库全量）实施细则

竖切顺序与落点（完整竖切 = ports 方法/record → `core/api/<domain>/XxxApi.java` 门面成员（四注解 `@ApiSince/@ApiMC/@ApiLoader` + 英文 Javadoc + `@zh`）→ `product/adapter` Fabric 实现 → `NeoMacrosFabric` 注册 / `registerGuestType` → 门禁测试）：

| 切片 | 任务 | 覆盖 | 关键新类型 |
|---|---|---|---|
| W1 world | T-502 | §5.1 全表 | `TabList`/`BossBar`（DATA） |
| W2 client | T-503 | §5.4 全表（packet 三件除外） | `Options` HANDLE 小集 |
| W3 player/chat/input | T-504 | §5.2/5.3/5.6 | `Image`（DATA，wraps `NativeImage`）、`Abilities` HANDLE |
| W4 hud/fs/net | T-505 | §5.5（player.screen/窗口归属 client）/5.7/5.8 | `FileHandle` HANDLE、`Ws` |
| W5 java/reflect 收尾 | T-506 | §5.9（`java.wrap` 如缺则补） | — |
| 管线收口 | T-507 | script-tests 用例 + docs/d.ts 重生成 + 报告 | — |
| 兼容壳回桥 | T-508 | §9 | — |

环境纪律：并发会话独立 worktree（从 main 新建 `t50x` 波次）；动工前 fresh read 确认 main 无未收口 WIP（注意 API2-F1 pending 遗留）；代码注释一律英文、只描述功能、禁任务号（`@zh` 例外）。

## 9. 兼容壳回桥流程（jsmacros-compat）

按 compat 侧四情形法实施（jsmacros-compat 无 git，直接改工作区）：

1. **情形 A**（显式 `defineStubs` 表成员，如 `World.playSound`）：在 `src/globals/<x>.js` 写真实现（桥接新 neo 成员），**并从文件尾部 `defineStubs` 表删除该条目**（表会覆盖手写实现）。
2. **情形 B**（`fillStubs` 自动填充成员，如实体基类写面）：直接在类中加实现即可，`if (name in proto) continue` 自动让位；整壳类（shells 15 类/实体 75 壳/背包 16 壳）需把目标类改手写子类。
3. **情形 C**（事件成员/新事件）：改 `src/globals/events/names.js` 的 MAPPED/UNSUPPORTED + `payload.js` 适配器 + 同步 `tools/audit.mjs` 的 `eventMeta`/`eventFixtures`。
4. **公共收尾**：`test/stub-neo.mjs` 补新 neo 面的内存桩 → `test/smoke.test.mjs` 把对应 `assert.throws(UnimplementedError)` 改行为断言 → `npm run audit`（门禁绿 + `docs/missing-apis.md` 重生成，§1 计数显著下降）→ `npm test`（20/20）→ 同步 `types/jsmacros-compat.d.ts` 与 README 的覆盖表。

## 10. 管线触点清单（每批适用）

1. doc-api：新成员四注解应标尽标（缺一构建失败）；新类 `@ApiClass(name, kind, wraps)` + `ScriptHandle`；
2. docs-builder：md/d.ts 重生成，快照审阅后晋升；`config.mts`/侧边栏仅模块集合变化时同步；
3. engine：新 guest 类走 `registerGuestType`；物种子类树落地时扩展派发（§3.1 规则 3）；
4. 事件：新事件 record + `@ApiEvent` + `ProductionEventSet.records()` + EventCatalog 白名单核对（`packet:receive/send` 进高流量忽略名单）；
5. script-tests：每模块 ≥1 用例（`script-tests/cases/`）；
6. 回写：本分册状态标注、12 号受影响节、README 决策登记、`plan/TASKS.md` + `plan/reports/`。

---

下一节：[README 决策登记](README.md)。