// Generates gap/tasks.json and the development-plan Markdown from gap.json.
// Every missing/partial/permanent member is either assigned to a work package
// or recorded in an explicit exclusion bucket; G-TRIAGE is the review fallback.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const dumpRoot = resolve(here, '..', '..');
const repoRoot = resolve(dumpRoot, '..');
const gap = JSON.parse(readFileSync(join(dumpRoot, 'gap', 'gap.json'), 'utf8'));
const doc13 = readFileSync(join(repoRoot, 'neomacros-docsx', '13-jsmacros-gap-api.md'), 'utf8');

const ACTIVE = new Set(['missing', 'partial', 'permanent-gap']);

// ---------------------------------------------------------------------------
// Server-only D members from 13 §2.3
// ---------------------------------------------------------------------------
const D_MEMBERS = new Map();
function addD(cls, names) {
  if (!D_MEMBERS.has(cls)) D_MEMBERS.set(cls, new Set());
  for (const name of names) D_MEMBERS.get(cls).add(name);
}
{
  const lines = doc13.split(/\r?\n/);
  let inside = false;
  for (const line of lines) {
    if (line.includes('### 2.3 服务端-only 物种状态')) { inside = true; continue; }
    if (inside && line.startsWith('## ')) break;
    if (!inside || !line.trim().startsWith('|')) continue;
    if (/^\|\s*-/.test(line) || line.includes('物种（JsMacros 壳）')) continue;
    const cells = line.split('|').slice(1, -1).map((c) => c.trim());
    if (cells.length < 3) continue;
    const clsCell = cells[0].replace(/`/g, '');
    const classes = clsCell.split(/\s*\/\s*/).map((s) => s.split(/\s+/)[0]).filter((s) => /(Helper|Inventory)$/.test(s));
    const members = cells[1].replace(/`/g, '').replace(/（[^）]*）/g, ' ').replace(/\([^)]*\)/g, ' ').split(/[\/\s、，,]+/).filter((s) => /^[A-Za-z_][A-Za-z0-9_]*$/.test(s));
    for (const cls of classes) addD(cls, members);
  }
}
// Explicit global/species exclusions not expressed as a single table row.
addD('FJsMacros', ['getProfile', 'getConfig', 'getServiceManager', 'getOpenContexts', 'runScript', 'wrapScriptRun', 'wrapScriptRunAsync']);
addD('FChat', ['getLogger']);
addD('FClient', ['getRegistryManager']);
addD('FPlayer', ['setGameMode', 'createPlayerInput', 'createPlayerInputsFromCsv', 'createPlayerInputsFromJson',
  'getCurrentPlayerInput', 'addInput', 'addInputs', 'clearInputs', 'setDrawPredictions', 'predictInput', 'predictInputs']);
addD('MobEntityHelper', ['isAttacking']);
addD('MerchantEntityHelper', ['refreshTrades', 'getExperience', 'hasCustomer']);
addD('AbstractPiglinEntityHelper', ['canBeZombified']);
addD('ChatHudLineHelper', ['getText', 'getCreationTick', 'deleteById']);
// NBT is deliberately not wrapped; every NBT-returning/taking member is D.
addD('EntityHelper', ['getNBT']);
addD('BlockDataHelper', ['getNBT']);
addD('ItemStackHelper', ['getNBT']);
addD('ItemHelper', ['getStackWithNbt']);

const D_CLASSES = new Set([
  'NBTElementHelper', 'NBTCompoundHelper', 'NBTListHelper', 'NBTNumberHelper', 'NbtPredicateHelper',
  'BlockPredicateHelper', 'StatePredicateHelper', 'StateHelper',
  'DyeColorHelper', 'FormattingHelper', 'DirectionHelper', 'FluidStateHelper',
  'RecipeHelper', 'TeamHelper', 'ChatHudLineHelper', 'ChatHistoryManager',
  'FakeServerCommandSource',
]);

// A-class: semantic equivalents that already exist; the fix belongs to the
// compat layer, not a new NeoMacros member.
const A_MEMBERS = new Map();
function addA(cls, names) {
  if (!A_MEMBERS.has(cls)) A_MEMBERS.set(cls, new Set());
  for (const name of names) A_MEMBERS.get(cls).add(name);
}
addA('FWorld', ['getWorldScanner']);
addA('FFS', ['toRelativePath', 'toRawFile', 'toRawPath', 'getRawAttributes']);
addA('FKeyBind', ['getKeyCode']);
addA('FPlayer', ['moveForward', 'moveBackward', 'moveStrafeLeft', 'moveStrafeRight']);
addA('FReflection', ['getReflect', 'newInstance0', 'createClassProxyBuilder']);

// E-class: permanent gaps (runtime Java compilation / dynamic class loading).
const E_MEMBERS = new Map();
addE('FReflection', ['compileJavaClass', 'getAllCompiledJavaClassVersions', 'getClassFromClassBuilderResult',
  'getCompiledJavaClass', 'createClassBuilder', 'createLibrary', 'createLibraryBuilder', 'loadJarFile']);
function addE(cls, names) {
  if (!E_MEMBERS.has(cls)) E_MEMBERS.set(cls, new Set());
  for (const name of names) E_MEMBERS.get(cls).add(name);
}
const CLASS_BUILDERS = new Set([
  'ClassBuilder', 'AnnotationBuilder', 'AnnotationArrayBuilder', 'BodyBuilder', 'ConstructorBuilder',
  'FieldBuilder', 'FieldInitializerBuilder', 'MethodBuilder', 'LibraryBuilder',
  'CombinedVariableClassLoader',
]);

// ---------------------------------------------------------------------------
// Work packages
// ---------------------------------------------------------------------------
const TASKS = [
  {
    id: 'G-001', title: '设计 spike：hud.layer 与 Draw2D 元素模型', phase: 'W0', priority: 'P0', size: 'M',
    status: 'design-first', repo: 'neomacros', classes: [], depends: [], targets: ['core/api/HudApi.java', 'core/api/render/RenderApi.java', 'ports/RenderPort.java', 'product/adapter/FabricRenderPort.java', 'product/adapter/FabricHudRegistry.java'],
    notes: '先定持久元素/图层/生命周期（add/remove/clear、z-index、parent/对齐、每帧重投），再拆组件类。T-516 的 hud.layer 模型未立项是本波的前置。',
    acceptance: '设计稿回写 12 号（§4.15）与 15 号；列出 API 签名、端口契约、版本差异、测试策略；经需求方确认后才进入 G-032/033/034。',
  },
  {
    id: 'G-002', title: '设计 spike：packet 管线与信任门', phase: 'W0', priority: 'P0', size: 'M', status: 'design-first', repo: 'neomacros', classes: [], depends: [], targets: ['ports/ClientPort.java', 'product/adapter/FabricClientPort.java', 'product/adapter/mixin/ClientPacketListenerMixin.java'],
    notes: 'T-517 后置中。需先定 packet:receive/send 事件、PacketBuffer 生命周期、高流量忽略名单、send 的信任门与权限键；再定 PacketByteBufferHelper 的成员映射。',
    acceptance: '设计稿含事件方向/可取消性、包过滤、缓冲区所有权、失败语义、版本门（1.21.6/26.x）、真机验证清单；需求方确认 send 权限。',
  },
  {
    id: 'G-003', title: '设计 spike：脚本 GUI 构建器', phase: 'W0', priority: 'P0', size: 'M', status: 'design-first', repo: 'neomacros', classes: [], depends: [], targets: ['core/api/player/PlayerApi.java', 'product/fabric/ScriptScreen.java', 'product/fabric/NeoMacrosFabric.java'],
    notes: 'T-521 P3。先定 Screen 句柄族与脚本构建器的边界、控件树/布局/事件回调、关闭与 Esc 语义、与 player.screen 只读面的关系。',
    acceptance: '设计稿含 ScreenSpec/控件 API、生命周期、与 OpenGL/26.x extractRenderState 的接线、测试策略；确认后再做 G-035/036。',
  },
  {
    id: 'G-004', title: 'compat 回桥波：216 个“NeoMacros 已具备、兼容壳仍为桩”', phase: 'W0', priority: 'P0', size: 'L', status: 'ready', repo: 'jsmacros-compat', classes: [], depends: [], targets: ['src/globals/**', 'src/support/**', 'docs/missing-apis.md', 'tools/audit.mjs'],
    notes: '最高 ROI：不新增 NeoMacros 面，只把 compat 的 UnimplementedError 桩改成桥接。230 = 216 个 compat stale + 14 个 A 类语义等价（世界扫描器、fs raw path、KeyBind keyCode、input movement、reflection getReflect 等）。',
    acceptance: 'npm test 20/20；npm run audit 完备性门禁绿；missing-apis.md 重新生成且对应条目消失；无 NeoMacros 源码改动。',
  },
  {
    id: 'G-005', title: 'compat 基线同步：CE 新增 39 成员 + 审计基线', phase: 'W0', priority: 'P1', size: 'S', status: 'ready', repo: 'jsmacros-compat', classes: [], depends: ['G-004'], targets: ['tools/jsmacros-inventory.json', 'tools/extract-jsmacros.mjs', 'src/**', 'docs/missing-apis.md'],
    notes: '当前 compat 基线仍是 1.9.2 wiki 索引；dump 已补全 CE 源码。把 39 个 CE 新增成员纳入 inventory/audit，避免“旧报告看不到”的盲区。',
    acceptance: 'inventory 与 dump 的类/成员差集可复现；audit 覆盖新增成员；报告记录 1.9.2→CE 的差异。',
  },
  {
    id: 'G-010', title: 'FWorld 世界查询补全', phase: 'W1', priority: 'P0', size: 'S', status: 'ready', repo: 'neomacros', classes: ['FWorld'],
    depends: [], targets: ['core/api/world/WorldApi.java', 'ports/WorldPort.java', 'product/adapter/FabricWorldPort.java'],
    notes: 'getWorldScanner 走 G-004 回桥；本任务只做 getRespawnPos、getSkyLight、getBlockLight、playSoundFile。playSoundFile 需确认与 world.playSound（声音事件 id）的边界；光照需 26.x/1.21.6 双实现。',
    acceptance: '4 个成员有 @ApiSince/@ApiMC/@ApiLoader；端口/适配器/StubAdapter 同步；core 单测 + e2e 脚本；四节点编译绿。',
  },
  {
    id: 'G-012', title: '实体基类状态面', phase: 'W1', priority: 'P0', size: 'L', status: 'ready', repo: 'neomacros',
    classes: ['EntityHelper', 'LivingEntityHelper', 'PlayerEntityHelper', 'ClientPlayerEntityHelper', 'MobEntityHelper'],
    depends: [], targets: ['core/api/entity/EntityApi.java', 'ports/EntityPort.java', 'product/adapter/FabricEntityPort.java', 'product/adapter/mixin/LocalPlayerMixin.java'],
    notes: 'EntityHelper: 发光/自定义名/速度（NBT 为 D）；LivingEntityHelper: 护甲槽位/触水呼吸/亡灵/getArmorToughness/isBaby/canSeeEntity；PlayerEntityHelper: 钓鱼浮标/攻击冷却/计分；ClientPlayer: calculateMiningSpeed + 冷却查询。部分成员是服务端-only（见 13 §2.3），先按 D 排除。',
    acceptance: '客户端可观察成员全部落地；需要的私有 getter 通过 interface+@Accessor+neomacros$ 暴露；无 raw MC 类型泄漏；四节点/e2e 绿。',
  },
  {
    id: 'G-013', title: '背包事件 payload 字段', phase: 'W1', priority: 'P0', size: 'S', status: 'ready', repo: 'neomacros',
    classes: ['EventClickSlot', 'EventDropSlot', 'EventOpenContainer', 'EventContainerUpdate', 'EventSlotUpdate'],
    depends: [], targets: ['core/event/def/inventory/*.java', 'ports/model/*', 'product/adapter/InventoryEventHubs.java', 'docs'],
    notes: '补 inventory/screen/type 等 payload 字段；能由现有 InventoryPort/事件 hub 提供的直接补，拿不到的登记 D。',
    acceptance: 'payload record 与事件目录同步；Mutable 面只暴露白名单字段；e2e 事件断言；docs 事件页重生成。',
  },
  {
    id: 'G-014', title: '玩家/实体事件 payload 字段', phase: 'W1', priority: 'P1', size: 'M', status: 'ready', repo: 'neomacros',
    classes: ['EventArmorChange', 'EventAttackBlock', 'EventAttackEntity', 'EventDamage', 'EventDeath', 'EventEXPChange',
      'EventFallFlying', 'EventHeal', 'EventHealthChange', 'EventHeldItemChange', 'EventHungerChange',
      'EventInteractBlock', 'EventInteractEntity', 'EventRiding', 'EventStatusEffectUpdate',
      'EventEntityDamaged', 'EventEntityHealed', 'EventEntityLoad', 'EventEntityUnload'],
    depends: [], targets: ['core/event/def/player/*.java', 'core/event/def/entity/*.java', 'ports/model/*', 'product/adapter/PortEventWiring.java'],
    notes: '按事件逐个补字段；oldItem/newItem/source/attacker 等能由 PortEventWiring 快照差量提供的补，服务端-only 的登记 D。',
    acceptance: '事件字段有 @ApiSince；Mutable 面白名单；e2e 至少覆盖每个事件的 1 个新字段；docs 重生成。',
  },
  {
    id: 'G-015', title: '聊天/世界/输入/屏幕事件字段', phase: 'W1', priority: 'P1', size: 'S', status: 'ready', repo: 'neomacros',
    classes: ['EventRecvMessage', 'EventSendMessage', 'EventChunkLoad', 'EventChunkUnload', 'EventKey', 'EventMouseScroll',
      'EventTitle', 'EventSignEdit', 'EventOpenScreen', 'EventSound', 'EventBlockUpdate', 'EventBossbar',
      'EventJoinServer', 'EventDimensionChange', 'EventLaunchGame', 'EventQuitGame', 'EventDisconnect', 'EventPlayerJoin', 'EventPlayerLeave'],
    depends: [], targets: ['core/event/def/chat/*.java', 'core/event/def/world/*.java', 'core/event/def/input/*.java', 'core/event/def/screen/*.java', 'ports/model/*'],
    notes: 'x/z 由 ChunkPos 提供；messageType/signature/playerName 等先 triage；能补的补，拿不到的登记 D。',
    acceptance: '同 G-014；chunk 坐标字段优先用 ChunkPos 句柄，不新增裸 x/z。',
  },
  {
    id: 'G-016', title: '缺失事件类型：资源包加载 / 名称变更', phase: 'W1', priority: 'P2', size: 'S', status: 'ready', repo: 'neomacros',
    classes: ['EventResourcePackLoaded', 'EventNameChange'],
    depends: [], targets: ['core/event/def/world/*.java', 'core/event/def/entity/*.java', 'ProductionEventSet', 'product/adapter/*Hub.java'],
    notes: '资源包加载事件需要 ResourceManager 回调；名称变更需要实体 customName 同步监听。两者都要评估高流量与去抖。',
    acceptance: '事件注册/目录/文档/e2e 全套；命名 domain:camelCase；可取消性明确。',
  },
  {
    id: 'G-017', title: '数据类小缺口：方块/区块', phase: 'W1', priority: 'P1', size: 'M', status: 'ready', repo: 'neomacros',
    classes: ['BlockDataHelper', 'BlockHelper', 'BlockStateHelper', 'ChunkHelper', 'HitResultHelper', 'Block', 'Entity'],
    depends: [], targets: ['core/api/types/Block.java', 'BlockState.java', 'Chunk.java', 'RayHit.java', 'ports/model/*'],
    notes: 'Block/BlockState/Chunk 的额外只读属性、HitResultHelper 的 Block/Entity 嵌套；Pos/Text/Plane 拆到 G-017B。',
    acceptance: '签名无 raw MC 类型；DATA 类不可变；单元测试；docs 类页重生成。',
  },
  {
    id: 'G-017B', title: '位置/文本/数学小批', phase: 'W1', priority: 'P1', size: 'S', status: 'ready', repo: 'neomacros',
    classes: ['Pos3D', 'Vec2D', 'Vec3D', 'TextBuilder', 'StyleHelper', 'Plane3D', 'HitResultHelper', 'Block', 'Entity'],
    depends: [], targets: ['core/api/types/Pos3D.java', 'Pos2D.java', 'TextComponent.java', 'RayHit.java', 'ports/model/Pos3d.java'],
    notes: 'Pos3D.toRawBlockPos/toMojangDoubleVector、Vec2D.compareTo、Vec3D.to3D、TextBuilder hover/click、Plane3D 新类、HitResultHelper 的 Block/Entity 嵌套。Plane3D 先确认是否值得建类（否则 D）。',
    acceptance: '数学边界测试；DATA 不可变；无 raw MC 类型；docs。',
  },
  {
    id: 'G-018', title: '数据句柄扩展：进度/统计', phase: 'W1', priority: 'P1', size: 'M', status: 'ready', repo: 'neomacros',
    classes: ['AdvancementHelper', 'AdvancementManagerHelper', 'AdvancementProgressHelper', 'StatsHelper'],
    depends: [], targets: ['core/api/player/PlayerApi.java', 'ports/model/*'],
    notes: 'Advancement 的 requirements/loot/recipes、Stats 的 raw/formatted；数据字段优先，不新增写动作。',
    acceptance: 'DATA/HANDLE 分类正确；缺席=undefined；单元测试 + docs。',
  },
  {
    id: 'G-018B', title: '数据句柄扩展：状态/计分/模组', phase: 'W1', priority: 'P1', size: 'M', status: 'ready', repo: 'neomacros',
    classes: ['StatusEffectHelper', 'ScoreboardObjectiveHelper', 'ScoreboardsHelper', 'BossBarHelper', 'ModContainerHelper', 'ServerInfoHelper', 'PlayerListEntryHelper'],
    depends: [], targets: ['core/api/player/PlayerApi.java', 'core/api/world/WorldApi.java', 'core/api/client/ClientApi.java', 'ports/model/*'],
    notes: '状态效果 flag、计分板/队伍、BossBar、模组元数据、服务器信息、tab list 条目；优先补数据字段，不新增写动作。',
    acceptance: 'DATA/HANDLE 分类正确；缺席=undefined；单元测试 + docs。',
  },
  {
    id: 'G-019', title: '容器通用面', phase: 'W1', priority: 'P0', size: 'M', status: 'ready', repo: 'neomacros',
    classes: ['Inventory', 'PlayerInventory', 'CraftingInventory', 'RecipeInventory'],
    depends: [], targets: ['core/api/player/PlayerApi.java', 'ports/InventoryPort.java', 'product/adapter/FabricInventoryPort.java'],
    notes: '鼠标槽位/槽位坐标/同步 id/raw container/openGui 先 triage；PlayerInventory 的 armor/offhand/hotbar 槽位与 Crafting/Recipe 的 grid/output 是重点。',
    acceptance: '槽位语义有文档（含 1.21.6/26.x 差异）；写动作走 Promise；e2e 打开容器断言。',
  },
  {
    id: 'G-020', title: '容器菜单面', phase: 'W2', priority: 'P1', size: 'L', status: 'ready', repo: 'neomacros',
    classes: ['FurnaceInventory', 'BrewingStandInventory', 'AnvilInventory', 'EnchantInventory', 'BeaconInventory',
      'GrindStoneInventory', 'SmithingInventory', 'StoneCutterInventory'],
    depends: ['G-019'], targets: ['core/api/player/PlayerApi.java', 'ports/InventoryPort.java', 'product/adapter/FabricInventoryPort.java'],
    notes: '每类补 menu data/slots 的只读面（燃料/熔炼/酿造/铁砧 cost/附魔/信标/砂轮/锻造/切石）。写动作（select/enchant/apply）单列，默认 Promise。',
    acceptance: '每个容器至少 1 个 e2e 打开+读取用例；菜单 data 缺失时 undefined，不抛；四节点。',
  },
  {
    id: 'G-021', title: '容器特化面：村民/马/织机/创造', phase: 'W2', priority: 'P1', size: 'L', status: 'ready', repo: 'neomacros',
    classes: ['VillagerInventory', 'HorseInventory', 'LoomInventory', 'CreativeInventory'],
    depends: ['G-019'], targets: ['core/api/player/PlayerApi.java', 'ports/InventoryPort.java', 'product/adapter/FabricInventoryPort.java', 'product/adapter/mixin/CreativeScreenAccessor.java'],
    notes: 'LoomInventory 当前无 NeoMacros 类（C）；CreativeInventory 22 项含 tab/搜索/hotbar 保存，需 CreativeModeInventoryScreen accessor；Villager/Horse 的 trades/saddle/armor 部分可能是 D（服务端/未公开）。',
    acceptance: 'Loom 类有 kind/wraps/构造；Creative 只读优先、写动作标风险；e2e 覆盖；docs 类树更新。',
  },
  {
    id: 'G-022', title: '物种姿态/展示面', phase: 'W2', priority: 'P1', size: 'L', status: 'ready', repo: 'neomacros',
    classes: ['ArmorStandEntityHelper', 'PandaEntityHelper', 'DisplayEntityHelper', 'TextDisplayDataHelper',
      'TextDisplayEntityHelper', 'BlockDisplayEntityHelper', 'ItemDisplayEntityHelper'],
    depends: [], targets: ['core/api/entity/SpeciesEntities.java', 'ports/EntityPort.java', 'product/adapter/FabricEntityPort.java'],
    notes: 'ArmorStand 六向 pose、Panda gene、Display billboard/visibility、TextDisplay data 拆解、Block/ItemDisplay 的 blockState/transform。Lerp 插值器/亮度覆盖按 13 §2.3 标 D。',
    acceptance: '需要 accessor 的走 interface+@Accessor；每类至少 1 个 e2e 实体断言；docs 类页。',
  },
  {
    id: 'G-023', title: '物种小缺口批（客户端可观察）', phase: 'W2', priority: 'P2', size: 'L', status: 'ready', repo: 'neomacros',
    classes: [], depends: ['G-012'], targets: ['core/api/entity/SpeciesEntities.java', 'ports/EntityPort.java', 'product/adapter/FabricEntityPort.java'],
    notes: '自动收纳未进 G-022 的物种类；先按 13 §2.3 排除服务端-only，再逐成员补客户端可观察值（变体/状态/物品/姿态）。',
    acceptance: '每类成员有 A/B/D 结论；B 类落地并测试；D 类回写 13 §2.3。',
  },
  {
    id: 'G-024', title: 'Options 根面 + Video', phase: 'W2', priority: 'P1', size: 'L', status: 'ready', repo: 'neomacros',
    classes: ['OptionsHelper', 'VideoOptionsHelper'],
    depends: [], targets: ['core/api/client/ClientApi.java', 'ports/ClientPort.java', 'ports/model/OptionsData.java', 'product/adapter/FabricClientPort.java'],
    notes: '当前 client.options 只有 fov/gamma/guiScale/renderDistance；补根面 + video/graphics 读写。OptionsKeyMappingsAccessor 已有，键位部分复用。',
    acceptance: '读写成对；写走 Promise 且回读校验；1.21.6/26.x 字段差异用 Stonecutter 门；docs 页。',
  },
  {
    id: 'G-024B', title: 'Options：Chat / Control / Accessibility', phase: 'W2', priority: 'P1', size: 'L', status: 'ready', repo: 'neomacros',
    classes: ['ChatOptionsHelper', 'ControlOptionsHelper', 'AccessibilityOptionsHelper'],
    depends: ['G-024'], targets: ['core/api/client/ClientApi.java', 'ports/ClientPort.java', 'ports/model/OptionsData.java', 'product/adapter/FabricClientPort.java'],
    notes: '聊天可见性/颜色/链接、控制灵敏度/反转/触屏/raw input、无障碍 narrator/字幕/背景透明度等。',
    acceptance: '同 G-024；控制选项复用 OptionsKeyMappingsAccessor。',
  },
  {
    id: 'G-024C', title: 'Options：Music / Skin', phase: 'W2', priority: 'P2', size: 'M', status: 'ready', repo: 'neomacros',
    classes: ['MusicOptionsHelper', 'SkinOptionsHelper'],
    depends: ['G-024'], targets: ['core/api/client/ClientApi.java', 'ports/ClientPort.java', 'ports/model/OptionsData.java', 'product/adapter/FabricClientPort.java'],
    notes: '音量分组/设备、披风/皮肤部件开关。',
    acceptance: '同 G-024。',
  },
  {
    id: 'G-025', title: 'RegistryHelper 全谱', phase: 'W2', priority: 'P1', size: 'L', status: 'ready', repo: 'neomacros',
    classes: ['RegistryHelper'],
    depends: [], targets: ['core/api/client/ClientApi.java', 'ports/ClientPort.java', 'product/adapter/FabricClientPort.java'],
    notes: 'client.registryIds/registryLookup 已有；补类型化 getItem/getBlock/getEntityType/enchantment/effect/particle 等与 id 解析。只读，无写面。',
    acceptance: '类型化返回 DATA/句柄；未知 id=undefined；单元测试 + docs。',
  },
  {
    id: 'G-026', title: '命令构建面：CommandBuilder', phase: 'W2', priority: 'P2', size: 'L', status: 'ready', repo: 'neomacros',
    classes: ['CommandBuilder'],
    depends: [], targets: ['core/api/CommandApi.java', 'core/api/CommandArg.java', 'core/api/CommandSpec.java', 'product/adapter/FabricCommandPort.java'],
    notes: 'CommandBuilder 是 JsMacros 的链式 builder；NeoMacros 走 CommandSpec 声明式。先 triage：能映射的做兼容工厂，不能映射的 D。',
    acceptance: '不新增重复 builder 体系；能映射的成员有 e2e；D 类写明理由。',
  },
  {
    id: 'G-026B', title: '命令构建面：Manager / Suggestions / Node / Source', phase: 'W2', priority: 'P2', size: 'M', status: 'ready', repo: 'neomacros',
    classes: ['CommandManager', 'SuggestionsBuilderHelper', 'CommandNodeHelper'],
    depends: ['G-026'], targets: ['core/api/CommandApi.java', 'core/api/CommandSpec.java', 'product/adapter/FabricCommandPort.java'],
    notes: '命令列表/注销/建议/节点/source；能映射到 command.register/execute/suggest 的补，内部派发实现登记 D。',
    acceptance: '同 G-026。',
  },
  {
    id: 'G-027', title: '物品面：ItemStack / Item', phase: 'W2', priority: 'P1', size: 'L', status: 'ready', repo: 'neomacros',
    classes: ['ItemStackHelper', 'ItemHelper'],
    depends: [], targets: ['core/api/types/ItemStack.java', 'ItemStackBuilder.java', 'ports/model/ItemStackData.java'],
    notes: '补 NBT 以外可观察面：maxCount、cooldown、destroy/place restrictions、creative tab、tool/wearable 判定。NBT 族 D。',
    acceptance: 'DATA 字段扩展；Builder 写面 Promise；未知=undefined；docs。',
  },
  {
    id: 'G-027B', title: '物品面：附魔 / 食物 / 创造', phase: 'W2', priority: 'P1', size: 'M', status: 'ready', repo: 'neomacros',
    classes: ['EnchantmentHelper', 'FoodComponentHelper', 'CreativeItemStackHelper'],
    depends: ['G-027'], targets: ['core/api/types/ItemStack.java', 'ports/model/EnchantmentData.java'],
    notes: '附魔兼容/等级/诅咒/宝藏、食物属性、创造物品附魔/隐藏标志。',
    acceptance: '同 G-027。',
  },
  {
    id: 'G-028', title: '交互/目标面', phase: 'W2', priority: 'P1', size: 'L', status: 'ready', repo: 'neomacros',
    classes: ['InteractionManagerHelper', 'Target', 'Break', 'Interact', 'BreakBlockResult', 'InteractionProxy'],
    depends: ['G-012'], targets: ['core/api/player/PlayerApi.java', 'ports/PlayerPort.java'],
    notes: 'Target override/checks、Break/Interact 回调和状态；与现有 player.target/interactBlock/attackBlock 去重。',
    acceptance: '动作走 Promise；只读状态属性化；override 语义文档化；e2e。',
  },
  {
    id: 'G-028B', title: '输入动作面：PlayerInput / KeyTracker', phase: 'W2', priority: 'P1', size: 'S', status: 'ready', repo: 'neomacros',
    classes: ['PlayerInput', 'KeyTracker'],
    depends: ['G-028'], targets: ['core/api/input/InputApi.java', 'ports/model/MovementSpec.java'],
    notes: 'PlayerInput 的 CSV/JSON/clone/movement 字段；KeyTracker 的按压状态。能映射到 input.movement/pressedKeys 的补，其余 D。',
    acceptance: '同 G-028。',
  },
  {
    id: 'G-029', title: '方块状态类型化查询', phase: 'W2', priority: 'P2', size: 'L', status: 'ready', repo: 'neomacros',
    classes: ['UniversalBlockStateHelper'],
    depends: [], targets: ['core/api/types/BlockState.java', 'ports/model/BlockStateData?', 'product/adapter/FabricWorldPort.java'],
    notes: '110 个类型化 getter（facing/axis/rail/wire/honey/level...）。不要逐个硬编码 110 个方法；先设计 state property 访问器 + 常用便捷属性，其余走 props 映射。',
    acceptance: '常用 20-30 个便捷成员 + 通用 props 查询；未知属性=undefined；版本差异表；docs。',
  },
  {
    id: 'G-030', title: 'CustomImage 绘制面', phase: 'W2', priority: 'P2', size: 'M', status: 'ready', repo: 'neomacros',
    classes: ['CustomImage'],
    depends: ['G-001'], targets: ['core/api/types/Image.java', 'core/api/HudApi.java', 'product/adapter/FabricHudRegistry.java'],
    notes: '像素读写/绘制/裁剪/文字/纹理注册；与 hud.layer 元素模型绑定。若 G-001 决定不建 texture 层，则本任务缩水为 Image DATA 只读。',
    acceptance: '像素操作有边界测试；上传/注册走游戏主线程；异步 Promise；docs。',
  },
  {
    id: 'G-031', title: '包级 API（packet）', phase: 'W3', priority: 'P2', size: 'XL', status: 'blocked', repo: 'neomacros',
    classes: ['PacketByteBufferHelper', 'EventRecvPacket', 'EventSendPacket'],
    depends: ['G-002'], targets: ['core/event/def/packet/*.java', 'ports/ClientPort.java', 'product/adapter/mixin/ClientPacketListenerMixin.java'],
    notes: 'T-517 后置；G-002 设计确认后才能开工。高流量包忽略名单、send 信任门、缓冲区生命周期必须先定。',
    acceptance: '事件/缓冲区/过滤/权限全套；真机验证；docs。',
  },
  {
    id: 'G-032', title: 'Draw2D 容器与图层', phase: 'W3', priority: 'P2', size: 'M', status: 'blocked', repo: 'neomacros',
    classes: ['Draw2D', 'IDraw2D'],
    depends: ['G-001'], targets: ['core/api/HudApi.java', 'core/api/render/*', 'product/adapter/FabricHudRegistry.java'],
    notes: '持久元素/图层/父子/缩放/z-index；Draw2D/IDraw2D 容器面。Draw2DElement 拆到 G-032B。',
    acceptance: '元素生命周期测试；每帧渲染；docs。',
  },
  {
    id: 'G-032B', title: 'Draw2DElement 基类与 Builder', phase: 'W3', priority: 'P2', size: 'M', status: 'blocked', repo: 'neomacros',
    classes: ['Draw2DElement'],
    depends: ['G-032'], targets: ['core/api/render/components/Draw2DElement.java', 'product/adapter/FabricHudRegistry.java'],
    notes: '元素基类 + Builder；作为 G-034/034B/034C 的父类先落地。',
    acceptance: '基类 API 稳定；继承测试；docs。',
  },
  {
    id: 'G-033', title: 'Draw3D 与基础 3D 组件', phase: 'W3', priority: 'P2', size: 'M', status: 'blocked', repo: 'neomacros',
    classes: ['Draw3D', 'Box', 'Line3D', 'RenderElement3D'],
    depends: ['G-001'], targets: ['core/api/render/RenderApi.java', 'ports/Draw3DPort.java', 'product/adapter/FabricDraw3DPort.java'],
    notes: 'box/line 已有原语；补 Draw3D 容器与 Box/Line3D 组件及 Builder。Surface/TraceLine 拆到 G-033B。',
    acceptance: '组件与现有原语不重复；e2e/截图验证；docs。',
  },
  {
    id: 'G-033B', title: '3D 面/追踪线组件', phase: 'W3', priority: 'P2', size: 'M', status: 'blocked', repo: 'neomacros',
    classes: ['Surface', 'TraceLine', 'EntityTraceLine'],
    depends: ['G-033'], targets: ['core/api/render/RenderApi.java', 'ports/Draw3DPort.java', 'product/adapter/FabricDraw3DPort.java'],
    notes: 'Surface/TraceLine/EntityTraceLine 及 Builder；复用 G-033 的组件基类。',
    acceptance: '同 G-033。',
  },
  {
    id: 'G-034', title: '2D 组件：Text / Rect / Line', phase: 'W3', priority: 'P2', size: 'L', status: 'blocked', repo: 'neomacros',
    classes: ['Text', 'Rect', 'Line'],
    depends: ['G-032'], targets: ['core/api/render/components/*', 'core/api/HudApi.java', 'product/adapter/FabricHudRegistry.java'],
    notes: 'Text/Rect/Line 及 Builder；任务卡列 67 个成员名、233 个签名，先做核心绘制/属性，再补 Builder 链。',
    acceptance: '每个组件有 add/remove/属性/Builder 测试；docs 类页。',
  },
  {
    id: 'G-034B', title: '2D 组件：Image / Item', phase: 'W3', priority: 'P2', size: 'M', status: 'blocked', repo: 'neomacros',
    classes: ['Image', 'Item'],
    depends: ['G-032'], targets: ['core/api/render/components/*', 'core/api/HudApi.java', 'product/adapter/FabricHudRegistry.java'],
    notes: 'Image/Item 及 Builder；Image 与 G-030 CustomImage 的纹理面共用底层。',
    acceptance: '同 G-034。',
  },
  {
    id: 'G-034C', title: '2D 组件基类：Alignable / RenderElement', phase: 'W3', priority: 'P2', size: 'S', status: 'blocked', repo: 'neomacros',
    classes: ['Alignable', 'RenderElement', 'RenderElementBuilder'],
    depends: ['G-032'], targets: ['core/api/render/components/*'],
    notes: '对齐/父容器/缩放/z-index 的基类；先于 G-034/034B 落地。',
    acceptance: '基类 API 稳定；组件继承测试；docs。',
  },
  {
    id: 'G-035', title: '脚本 GUI 核心', phase: 'W3', priority: 'P2', size: 'L', status: 'blocked', repo: 'neomacros',
    classes: ['ScriptScreen', 'IScreen'],
    depends: ['G-003'], targets: ['core/api/player/PlayerApi.java', 'product/fabric/ScriptScreen.java', 'product/fabric/NeoMacrosFabric.java'],
    notes: 'T-521 P3；脚本自建 Screen 的 open/close/onRender/onClose。',
    acceptance: 'Screen 生命周期/回调/关闭语义；e2e 打不开也不崩；docs。',
  },
  {
    id: 'G-036', title: '脚本 GUI 控件：基础四类', phase: 'W3', priority: 'P2', size: 'L', status: 'blocked', repo: 'neomacros',
    classes: ['ButtonWidgetHelper', 'ButtonBuilder', 'TexturedButtonBuilder', 'CheckBoxWidgetHelper', 'CheckBoxBuilder',
      'SliderWidgetHelper', 'SliderBuilder', 'TextFieldWidgetHelper', 'TextFieldBuilder'],
    depends: ['G-035'], targets: ['core/api/render/screen/*', 'product/fabric/ScriptScreen.java'],
    notes: '先做 Button/CheckBox/Slider/TextField 四类控件与 Builder；其余控件拆到 G-036B。',
    acceptance: '控件状态/回调/布局测试；docs。',
  },
  {
    id: 'G-036B', title: '脚本 GUI 控件：次级控件', phase: 'W3', priority: 'P2', size: 'M', status: 'blocked', repo: 'neomacros',
    classes: ['AbstractWidgetBuilder', 'CyclingButtonWidgetHelper', 'CyclicButtonBuilder', 'LockButtonWidgetHelper', 'LockButtonBuilder', 'ClickableWidgetHelper'],
    depends: ['G-036'], targets: ['core/api/render/screen/*', 'product/fabric/ScriptScreen.java'],
    notes: 'Cycling/Lock/Clickable 与抽象 Builder 基类；在基础四类稳定后补。',
    acceptance: '同 G-036。',
  },
  {
    id: 'G-037', title: 'FPlayer P3：签名/全景', phase: 'W3', priority: 'P2', size: 'S', status: 'blocked', repo: 'neomacros',
    classes: ['FPlayer'], depends: ['G-003'], targets: ['core/api/player/PlayerApi.java', 'product/adapter/FabricPlayerPort.java', 'product/fabric/NeoMacrosFabric.java'],
    notes: 'writeSign 依赖 SignScreen/服务器签名包；takePanorama 依赖截图/全景管线（T-520 P3）。',
    acceptance: '真机验证；失败语义明确；docs。',
  },
  {
    id: 'G-038', title: '纹理/注册纹理面', phase: 'W3', priority: 'P2', size: 'M', status: 'blocked', repo: 'neomacros',
    classes: ['FHud'], depends: ['G-001'], targets: ['core/api/HudApi.java', 'core/api/types/Image.java', 'product/adapter/FabricHudRegistry.java'],
    notes: 'FHud.createTexture/getRegisteredTextures；T-518 后置。与 G-030 的 Image 纹理注册合并评审。',
    acceptance: '纹理生命周期/去重/清理；docs。',
  },
  {
    id: 'G-039', title: '世界扫描 DSL 对齐（compat）', phase: 'W2', priority: 'P1', size: 'L', status: 'ready', repo: 'jsmacros-compat',
    classes: ['WorldScanner', 'WorldScannerBuilder'], depends: ['G-004'], targets: ['src/globals/world.js', 'src/support/surface-data.js', 'tools/audit.mjs'],
    notes: 'NeoMacros 用 world.findBlocks/scanBlocks options DSL；不新建 JsMacros filter 类。把 WorldScanner/Builder 的成员映射到 options 对象，filter 类保持 D/桩。',
    acceptance: '扫描/过滤/排序/上限语义对齐；npm test；missing-apis 更新。',
  },
  {
    id: 'G-040', title: '永久缺口（不实施）', phase: '—', priority: '—', size: '—', status: 'no-op', repo: 'neomacros',
    classes: ['ClassBuilder'], depends: [], targets: [],
    notes: '运行时 Java 编译/动态类生成/Jar 加载（ClassBuilder 族 + CombinedVariableClassLoader + FReflection compile 族）。按 13 §2.1 拍板④不实施；脚本用 neo.java/neo.reflect 覆盖。',
    acceptance: '无代码；若出现新证据，先在 13 号重新拍板。',
  },
];

const TASK_BY_ID = new Map(TASKS.map((t) => [t.id, t]));
const CLASS_TASK = new Map();
for (const t of TASKS) for (const cls of t.classes) CLASS_TASK.set(cls, t.id);

const GLOBAL_TASK = {
  FWorld: 'G-010', FPlayer: 'G-037', FReflection: 'G-004', FHud: 'G-038',
  FClient: 'G-031', FKeyBind: 'G-004', FFS: 'G-004',
};
const EVENT_TASK = {
  EventClickSlot: 'G-013', EventDropSlot: 'G-013', EventOpenContainer: 'G-013', EventContainerUpdate: 'G-013', EventSlotUpdate: 'G-013',
  EventArmorChange: 'G-014', EventAttackBlock: 'G-014', EventAttackEntity: 'G-014', EventDamage: 'G-014', EventDeath: 'G-014',
  EventEXPChange: 'G-014', EventFallFlying: 'G-014', EventHeal: 'G-014', EventHealthChange: 'G-014', EventHeldItemChange: 'G-014',
  EventHungerChange: 'G-014', EventInteractBlock: 'G-014', EventInteractEntity: 'G-014', EventRiding: 'G-014',
  EventStatusEffectUpdate: 'G-014', EventEntityDamaged: 'G-014', EventEntityHealed: 'G-014', EventEntityLoad: 'G-014', EventEntityUnload: 'G-014',
  EventRecvMessage: 'G-015', EventSendMessage: 'G-015', EventChunkLoad: 'G-015', EventChunkUnload: 'G-015', EventKey: 'G-015',
  EventMouseScroll: 'G-015', EventTitle: 'G-015', EventSignEdit: 'G-015', EventOpenScreen: 'G-015', EventSound: 'G-015',
  EventBlockUpdate: 'G-015', EventBossbar: 'G-015', EventJoinServer: 'G-015', EventDimensionChange: 'G-015',
  EventLaunchGame: 'G-015', EventQuitGame: 'G-015', EventDisconnect: 'G-015', EventPlayerJoin: 'G-015', EventPlayerLeave: 'G-015',
  EventResourcePackLoaded: 'G-016', EventNameChange: 'G-016',
  EventRecvPacket: 'G-031', EventSendPacket: 'G-031',
};
const DATA_TASK = {
  BlockDataHelper: 'G-017', BlockHelper: 'G-017', BlockStateHelper: 'G-017', ChunkHelper: 'G-017', HitResultHelper: 'G-017',
  Pos3D: 'G-017B', Vec2D: 'G-017B', Vec3D: 'G-017B',
  TextBuilder: 'G-017B', StyleHelper: 'G-017B', Plane3D: 'G-017B',
  AdvancementHelper: 'G-018', AdvancementManagerHelper: 'G-018', AdvancementProgressHelper: 'G-018', StatsHelper: 'G-018',
  StatusEffectHelper: 'G-018B', ScoreboardObjectiveHelper: 'G-018B', ScoreboardsHelper: 'G-018B', BossBarHelper: 'G-018B',
  ModContainerHelper: 'G-018B', ServerInfoHelper: 'G-018B', PlayerListEntryHelper: 'G-018B',
  KeyTracker: 'G-028B',
  OptionsHelper: 'G-024', VideoOptionsHelper: 'G-024',
  ChatOptionsHelper: 'G-024B', ControlOptionsHelper: 'G-024B', AccessibilityOptionsHelper: 'G-024B',
  MusicOptionsHelper: 'G-024C', SkinOptionsHelper: 'G-024C',
  RegistryHelper: 'G-025',
  CommandBuilder: 'G-026', CommandManager: 'G-026B', SuggestionsBuilderHelper: 'G-026B', CommandNodeHelper: 'G-026B', FakeServerCommandSource: 'G-026B',
  ItemStackHelper: 'G-027', ItemHelper: 'G-027', EnchantmentHelper: 'G-027B', FoodComponentHelper: 'G-027B', CreativeItemStackHelper: 'G-027B',
  InteractionManagerHelper: 'G-028', Target: 'G-028', Break: 'G-028', Interact: 'G-028',
  BreakBlockResult: 'G-028', InteractionProxy: 'G-028', PlayerInput: 'G-028B',
  UniversalBlockStateHelper: 'G-029',
  CustomImage: 'G-030',
  PacketByteBufferHelper: 'G-031',
};

const D_SCOPES = new Set(['internal', 'core-internal', 'excluded', 'removed-upstream']);

function classTask(c) {
  if (D_SCOPES.has(c.scope)) return 'EXCL-INTERNAL';
  if (c.status === 'permanent-gap') return 'EXCL-E';
  if (CLASS_BUILDERS.has(c.simpleName)) return 'EXCL-E';
  if (c.scope === 'worldscanner') {
    return ['WorldScanner', 'WorldScannerBuilder'].includes(c.simpleName) ? 'G-039' : 'EXCL-D';
  }
  if (D_CLASSES.has(c.simpleName)) return 'EXCL-D';
  if (c.scope === 'global') return GLOBAL_TASK[c.simpleName] ?? 'EXCL-D';
  if (c.scope === 'entity') return CLASS_TASK.get(c.simpleName) ?? 'G-023';
  if (c.scope === 'inventory') return CLASS_TASK.get(c.simpleName) ?? 'G-020';
  if (c.scope === 'event-payload') return EVENT_TASK[c.simpleName] ?? 'G-TRIAGE';
  if (c.scope === 'screen') {
    if (['ButtonWidgetHelper', 'ButtonBuilder', 'TexturedButtonBuilder', 'CheckBoxWidgetHelper', 'CheckBoxBuilder',
      'SliderWidgetHelper', 'SliderBuilder', 'TextFieldWidgetHelper', 'TextFieldBuilder'].includes(c.simpleName)) return 'G-036';
    return 'G-036B';
  }
  if (c.scope === 'render') {
    const fqn = c.jsmacrosClass;
    if (['Draw2D', 'IDraw2D'].includes(c.simpleName)) return 'G-032';
    if (['Draw2DElement'].includes(c.simpleName) || fqn.includes('.components.Draw2DElement.Builder')) return 'G-032B';
    if (['Draw3D', 'Box', 'Line3D', 'RenderElement3D'].includes(c.simpleName)
      || fqn.includes('.components3d.Box.Builder') || fqn.includes('.components3d.Line3D.Builder')) return 'G-033';
    if (fqn.includes('.components3d.Surface') || fqn.includes('.components3d.TraceLine')
      || fqn.includes('.components3d.EntityTraceLine')) return 'G-033B';
    if (c.simpleName === 'ScriptScreen' || c.simpleName === 'IScreen') return 'G-035';
    if (['Text', 'Rect', 'Line'].includes(c.simpleName)
      || fqn.includes('.components.Text.Builder') || fqn.includes('.components.Rect.Builder')
      || fqn.includes('.components.Line.Builder')) return 'G-034';
    if (['Image', 'Item'].includes(c.simpleName)
      || fqn.includes('.components.Image.Builder') || fqn.includes('.components.Item.Builder')) return 'G-034B';
    if (['Alignable', 'RenderElement', 'RenderElementBuilder'].includes(c.simpleName)) return 'G-034C';
    return 'G-034C';
  }
  if (c.scope === 'data-support') {
    if (c.jsmacrosClass.includes('HitResultHelper.')) return 'G-017';
    return DATA_TASK[c.simpleName] ?? 'G-TRIAGE';
  }
  if (c.scope === 'core-library') return 'EXCL-E';
  return 'G-TRIAGE';
}

const assignments = new Map(TASKS.map((t) => [t.id, []]));
const exclusions = {
  'EXCL-INTERNAL': [],
  'EXCL-D': [],
  'EXCL-E': [],
  'G-TRIAGE': [],
};
const memberTask = (c, m) => {
  const d = D_MEMBERS.get(c.simpleName);
  if (d && d.has(m.name)) return 'EXCL-D';
  const a = A_MEMBERS.get(c.simpleName);
  if (a && a.has(m.name)) return 'G-004';
  const e = E_MEMBERS.get(c.simpleName);
  if (e && e.has(m.name)) return 'EXCL-E';
  if (CLASS_BUILDERS.has(c.simpleName)) return 'EXCL-E';
  const task = classTask(c);
  return task;
};

for (const c of gap.classes) {
  const active = c.members.filter((m) => ACTIVE.has(m.status));
  if (!active.length) continue;
  for (const m of active) {
    const taskId = memberTask(c, m);
    const item = {
      class: c.simpleName,
      fqn: c.jsmacrosClass,
      name: m.name,
      kind: m.kind,
      signature: m.signature,
      status: m.status,
      target: m.target,
      reason: m.reason || m.evidence || '',
      evidence: m.evidence || '',
    };
    if (taskId === 'G-004') assignments.get('G-004').push(item);
    else if (taskId.startsWith('EXCL-')) exclusions[taskId].push(item);
    else if (taskId === 'G-TRIAGE') exclusions['G-TRIAGE'].push(item);
    else if (assignments.has(taskId)) assignments.get(taskId).push(item);
    else exclusions['G-TRIAGE'].push(item);
  }
}

// compat stale and CE-new members are sourced from the summary, not the
// missing list: they are already covered on the NeoMacros side.
for (const s of gap.summary.compatStale) {
  assignments.get('G-004').push({
    class: s.class.split('.').pop(), fqn: s.class, name: s.member, kind: 'member',
    signature: '', status: 'covered', target: s.target, reason: 'compat stub → NeoMacros 已具备', evidence: 'compat stale',
  });
}
for (const n of gap.summary.newMembers) {
  assignments.get('G-005').push({
    class: n.class.split('.').pop(), fqn: n.class, name: n.member, kind: 'member',
    signature: '', status: n.status, target: null, reason: 'CE 新增成员，旧 1.9.2 基线不含', evidence: 'new member',
  });
}

const tasks = TASKS.map((t) => {
  const members = assignments.get(t.id) ?? [];
  const uniqueClasses = [...new Set(members.map((m) => m.class))];
  return {
    ...t,
    classes: [...new Set([...t.classes, ...uniqueClasses])],
    memberCount: members.length,
    members,
  };
});

const totalAssigned = tasks.reduce((n, t) => n + t.memberCount, 0);
const compatStaleCount = gap.summary.compatStale.length;
const compatNewCount = gap.summary.newMembers.length;
const activeAssigned = totalAssigned - compatStaleCount - compatNewCount;
const g004A = (assignments.get('G-004') ?? []).length - compatStaleCount;
const g039 = (assignments.get('G-039') ?? []).length;
const neomacrosAssigned = activeAssigned - g004A - g039;
const compatAssigned = g004A + g039;

const tasksJson = {
  meta: {
    schemaVersion: 1,
    generatedBy: 'tools/gap/gen-development-plan.mjs',
    jsmacrosCommit: gap.meta.jsmacrosCommit,
    neomacrosCommit: gap.meta.neomacrosCommit,
    source: 'gap/gap.json',
    activeStatuses: [...ACTIVE],
    note: 'G-004/G-005/G-039 在 jsmacros-compat；G-040 为不实施；EXCL-* 为不开发项。',
  },
  summary: {
    tasks: tasks.length,
    assignedMembers: totalAssigned,
    activeAssigned,
    neomacrosAssigned,
    compatAssigned,
    compatStale: compatStaleCount,
    compatNew: compatNewCount,
    exclusions: Object.fromEntries(Object.entries(exclusions).map(([k, v]) => [k, v.length])),
    unassigned: exclusions['G-TRIAGE'].length,
    activeTotal: activeAssigned + exclusions['EXCL-D'].length + exclusions['EXCL-E'].length,
  },
  tasks,
  exclusions,
};

writeFileSync(join(dumpRoot, 'gap', 'tasks.json'), JSON.stringify(tasksJson, null, 2) + '\n', 'utf8');
console.log(JSON.stringify(tasksJson.summary, null, 2));
console.log('\ntasks:');
for (const t of tasks) {
  console.log(`${t.id.padEnd(8)} ${String(t.memberCount).padStart(3)} members  ${t.priority.padEnd(2)} ${t.status.padEnd(12)} ${t.title}`);
}
if (exclusions['G-TRIAGE'].length) {
  console.log('\nTRIAGE (first 80):');
  const counts = {};
  for (const m of exclusions['G-TRIAGE']) counts[m.class] = (counts[m.class] || 0) + 1;
  console.log(Object.entries(counts).sort((a, b) => b[1] - a[1]).map(([c, n]) => `${c}:${n}`).join(' '));
}
