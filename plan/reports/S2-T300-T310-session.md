# 会话报告：S2 渲染收口 + T-300/T-301 + T-310（2026-11-01）

## 本窗口完成

### 前置修复
- PortEventWiringTest 本地 StubAdapter 的 player()/entity() 每次调用返回新实例，
  PortEventWiring 注册的 sink 落在测试不可达实例上；改为共享字段并启用全部
  3 个 @Disabled 测试（5/5 绿）。

### T-101b 收口（7 件，t101b-mixin-events3 → main）
- entity:damage/heal：per-tick 健康快照差量（SelfStateTracker，32 格半径，
  离域清位、首见不触发、本地玩家排除）
- player:effect：PlayerPort.activeEffects() + EffectSnapshot；add/remove/等级
  变化三态差量
- world:blockUpdate/chunkLoad：ClientLevelWorldMixin（sendBlockUpdated +
  onChunkLoaded，javap 实证）
- world:chunkUnload：ClientChunkCacheMixin（drop；ChunkPos 是 record，x()/z()）
- world:bossbar：BossUpdateMixin 用只读 Handler 重派发 ClientboundBossEventPacket
- 修正：合并产生的 hub 桥重复注册（事件双发）+ 删除死代码 EntityEventHub

### T-106 26.x（main）
- 激活 26.1.2/26.2/26.3 三节点，全部编译绿并出 fabric jar
- 9 节点 fabric API 版本按 Modrinth 核实修正（26.1.2→0.155.3 等 5 处）
- **关键发现**：Stonecutter 0.8.4 只预处理版本化项目自有源码树，外部
  srcDir 绕过预处理管线；active 节点直接编译原始源码。因此 adapter/fabric
  源码迁入 product 分支树；跨版本差异走 product/build.gradle.kts 的
  stonecutter replacements（KEYBOARD/KEYSYM、gui.hud/gui），源码按 active
  节点（最新 26.3）的 API 书写

### S2 渲染（t200b-render-api → main）
- Draw3DPort + FabricDraw3DPort：原版 gizmo 管线（Gizmos.addGizmo +
  GizmoPrimitives.addLine，box=12 边线框），COW 形状列表跨线程安全
- NeomacrosAdapter.draw3d()；neo.render 门面（构造注入，可单测）
- FabricHudRegistry 修复 neo.hud 死 API（HudApi 此前无任何实现与注册）
- Draw2D sprite 延后：26.3 GuiGraphicsExtractor 无 blit，贴图需 renderpearl
  RenderPipeline+TextureSetup，独立增量

### T-300/T-301（main）
- ErrorPresenter：节流聊天通知（3s 窗口）+ 20 条环形历史（3 个单测）
- 修复 ScriptRuntime.setErrorHandler 从未接线——运行时脚本错误此前静默丢失
- ScriptScreen（26.3 extractRenderState + MultiLineEditBox + Done）；
  /neomacros errors 与 /neomacros open <script>
- 26.3 实证：Minecraft.setScreenAndShow、ClientCommands.argument、
  ClientCommands.literal（FabricClientCommandSource 泛型）

### T-310
- en/zh 双语 getting-started + API landing 页面 + home；
  npm install + vitepress build 验证（6 页 HTML 产出）
- 遗留：docs-builder 生成页面接入 vitepress 构建管线

## 验证

- 全量 build 绿（26.1.2/26.2/26.3），214 tests 0 failures
- 留给人工 runClient：mixin 事件族游戏内冒烟、gizmo 渲染目视、
  ScriptScreen 打开、错误链路触发

## 遗留（后续窗口）

1. T-302 编辑器 GUI / T-303 管理面板（26.3 Screen 语义已摸清）
2. S2 sprite（renderpearl 管线）
3. T-106 1.x：obf 映射模式 loom 设置 + Java 17/21 工具链 + 1.18.2 可行性（G-R3）
4. T-001 CI：git remote 配置 → actionlint + PR 实跑
