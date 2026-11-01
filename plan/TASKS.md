# NeoMacros 任务看板

> **新会话/新 agent 从这里开始**（2026-10-31 建立交接入口）：
> 1. 读设计基线 `../../neomacros-docs/README.md`（已确认决策，不可单方面推翻）；
> 2. 读执行协议 `../../neomacros-docs/09-development-plan.md`（§0 Agent 使用说明 / DoD / 泳道 / E2E 规格）；
> 3. 在本看板认领任务：动工前标 `in-progress` + Owner/分支，完成后标 `done` 并写 `plan/reports/<ID>.md` 报告。
>
> 当前进度（2026-11-01 第五次审计）：**done=31, partially=1, pending=0, 全量 build 绿（1.21.6/26.1.2/26.2/26.3 四节点，216 tests）**。本窗口新收口 **T-106 1.x 激活**：1.21.6 以 mapped 工具链（现代 fabric-loom 插件 id + fabric.loom.disableObfuscation=false → layered Mojmap + modImplementation）接入并出 fabric jar；旧插件 id（net.fabricmc.fabric-loom）被 loom 1.19 硬编码强制 unobf+finalize，恰为 26.x 所需——插件选择下沉到按节点的 buildscript（product/build.gradle.kts vs unobf.gradle.kts，共享逻辑在 node-common.gradle.kts 显式 API 形式 + buildSrc/NeomacrosNode.kt replacements）。跨版本两机制定界：**replace 对**（仅当 to 拼写不出现于共享源码——stonecutter 对条件另一侧自动反向应用）与 **//? 交换**（结构性差异：Screen render/extractRenderState、ChatComponent 入口、按键分类、ChunkPos record/字段、gizmos vs WorldRenderEvents、setScreenAndShow/setScreen）。**G-R3 决策**：1.18.2 不纳入里程碑矩阵（MatrixStack 世代+无 MultiLineEditBox+HudRenderCallback+ mixin 漂移，见 02 §9），最低里程碑版本=1.21.6；1.19.4~1.21.4 为下一梯队候选。**partially 仅剩 T-001**：CI 需用户配置 git remote 地址。游戏内人工验证项（G 级）：四节点 runClient、编辑器/面板/信任链路/mixin 事件族/gizmo/sprite 目视。
> R1/R2 已关闭（T-022，结论见 08 §7）；bindgen 完成（T-023）：值中立绑定 + ProxyObject 活属性 + 权限包装 + @ApiMC 节点裁剪，双通道同一性有脚本级测试。
> 已解锁可认领：**T-031（模块管理器——关键路径，前置全就绪）**、**T-030**（adapter 最小产品节点；首个 adapter 代码前必做双向 chisel 验证，见 T-000 报告遗留）、**T-032**（配置系统）；T-001 闭环（配 remote → PR 实跑 → 改标 done）。
> 关键路径剩余：T-031 → T-040（E2E-A）→ G0。
> 快速自验命令（仓库根）：`./gradlew build` → `bash scripts/smoke-build.sh`；引擎：`./gradlew :engine:test`；文档管线：`./gradlew :doc-api:publishToMavenLocal && ./gradlew -p docs-builder check -PtscPath=<bin/tsc>`。

> 来源：[09-开发计划书](../../neomacros-docs/09-development-plan.md)。认领协议见计划书 §0：
> 动工前把任务标 `in-progress` 并填 Owner/分支/日期；完成后标 `done` 并在备注附报告链接。
> 同一任务同时只能有一个 owner；泳道内非标注可并行的任务不并行认领。

| ID | 任务 | 阶段 | 状态 | Owner | 分支 | 备注 |
|---|---|---|---|---|---|---|
| T-000 | 仓库骨架与构建脚手架 | S0 | **done** | session-agent | main | [报告](reports/T-000.md)；loom 1.19/Gradle 9.8 升级与 stonecutter 根脚本语义实证已回写架构文档 02 |
| T-001 | CI 工作流骨架 | S0 | **partially-done** | session-agent | t001-ci | [报告](reports/T-001.md)；workflow 全交付+CI 命令本地实证；actionlint 与 PR 实跑待配置 git remote 后闭环 |
| T-010 | doc-api 注解集 | S0 | **done** | session-agent | t010-doc-api | [报告](reports/T-010.md)；10 注解 + 18 单测绿 + maven 坐标已验证 |
| T-011 | ports 中立类型与最小端口集 | S0 | **done** | session-agent | t011-ports | [报告](reports/T-011.md)；7 record + 5 端口 + ArchUnit 共享守卫（testFixtures） |
| T-020 | core 事件框架 | S0 | **done** | session-agent | t020-event-framework | [报告](reports/T-020.md)；双通道 bus+watchdog+通配 43 测试绿，框架包覆盖率 97.7%；P0 基础事件载荷已备 |
| T-021 | core Runtime·线程·Engine SPI | S0 | **done** | session-agent | t021-runtime-threading | [报告](reports/T-021.md)；Runtime/线程池/事件循环/异步原语/Engine SPI/死锁防护 39 测试绿（全量 82） |
| T-022 | engine GraalJS PoC ★R1 | S0 | **done** | session-agent | t022-graaljs-engine | [报告](reports/T-022.md)；R1 结：FileSystem SPI 原生拦截可行；R2 结：24.2.2 跑通 Java 17（预热 968→20ms）；08 §7 已回写 |
| T-023 | core ApiModel·绑定注册 | S0 | **done** | session-agent | t023-bindgen | [报告](reports/T-023.md)；值中立绑定+ProxyObject 活属性+权限包装+节点裁剪，core 11 + 引擎集成 6 全绿；**关键路径打通，T-031 已解锁** |
| T-024 | 文档管线骨架（docs-builder） | S0 | **done** | session-agent | t024-docs-builder | [报告](reports/T-024.md)；双 doclet + 快照回归 + tsc 实跑全绿；骨架期约定见其 README |
| T-030 | adapter/fabric 最小产品节点 | S0 | **done** | session-agent | t030-product-node | [报告](reports/T-030.md)；Lifecycle/Chat 端口+mixin+Bootstrap 全绿；游戏内完整启动 0 mixin 错误；reload 移交 T-031 |
| T-031 | 模块管理器 | S0 | **done** | session-agent | t031-module-manager | [报告](reports/T-031.md)；收口：sleep-settles 游戏内 PASS（真因=旧 record 的 console.log 抛错）；文件监视器由 T-032 交付；单模块 reload 移交 T-303 |
| T-032 | 配置系统 | S0 | **done** | session-agent | t032-config | [报告](reports/T-032.md)；ConfigManager+Codec+CoreOptions+watch 接线全绿；补 T-031 遗留#2 文件监视器 |
| T-033 | storage 模块 | S0 | **done** | session-agent | t033-storage | [报告](reports/T-033.md)；StorageApi/ModuleStorage/防抖/快照+Proxy 包装全绿；真脚本 put→get→落盘集成测试 |
| T-040 | E2E-A 测试骨架 | S0 | **done** | session-agent | t040-e2ea | [报告](reports/T-040.md)；8 基线场景绿；修复 payload guest 化等五项跨层缺陷 |
| T-041 | E2E-G 套件骨架 | S0 | **done** | session-agent | t041-e2eg（收口 33f4b37→main） | [报告](reports/T-041.md)；收口：suite 5/5 ALL PASS + verifyGameE2E 绿 + 33f4b37 独立复核全绿；根因三项定性已回写 08 §8；遗留移交见报告（watchdog 接线→T-100、quitOnDone 复跑、cases 同源机制） |
| T-100 | 权限信任门 | S1 | **done** | session-agent | t100-trust | [报告](reports/T-100.md)；TrustManager+可点击提示+trust 命令 ×5 单测绿；信任哈希口径=模块名+声明集（回写 05） |
| T-101 | lane-world（world/entity/player） | S1 | **partially-done** | session-agent-b | t101-lane-world | [报告](reports/T-101.md)；版本无关层全交付+player/world 事件族 17 件全绿；剩余 mixin 事件族→T-101b、游戏内 observe 待人工进世界；**合入 main 需等 T-100 提交（同改 NeoMacrosFabric）** |
| T-101b | lane-world mixin 事件族 | S1 | **done** | session-agent | t101b-mixin-events3 | [报告](reports/T-101b.md)；11 件全交付：entity:damage/heal=per-tick 健康快照差量（PortEventWiring SelfStateTracker，32 格半径、离域清位防重放）、world:blockUpdate/chunkLoad=ClientLevelWorldMixin、world:chunkUnload=ClientChunkCacheMixin、world:bossbar=BossUpdateMixin 重派发 packet、player:effect=PlayerPort.activeEffects()+EffectSnapshot 差量；新增 4 payload record 入 ProductionEventSet；修复合并产生的 hub 桥重复注册；删除死代码 EntityEventHub |
| T-102 | lane-inventory | S1 | **done** | session-agent | t102-inventory | [报告](reports/T-102.md)；InventoryPort+FabricInventoryPort 只读查询集绿；写操作随 P1 安全评审 |
| T-103 | lane-input | S1 | **done** | session-agent | t103-input | [报告](reports/T-103.md)；InputPort+FabricInputPort（26.3 keymapping/v1 新 API）+滚轮 mixin+InputApi；输入模拟延后 |
| T-104 | lane-fs/net | S1 | **done** | session-agent | t104-fs-net | [报告](reports/T-104.md)；FsApi+NetApi 门面+沙箱边界+逃逸拒绝 ×3 单测绿；WebSocket 延后 |
| T-105 | lane-command | S1 | **done** | session-agent | t105-command | [报告](reports/T-105.md)；CommandApi+FabricCommandPort（literal+args remainder）×3 单测绿；类型化 DSL 延后 |
| T-106 | 全节点矩阵 + 1.18.2 可行性 ★R3 | S1 | **done**（矩阵收口，G-R3 已决） | session-agent | t106-node-matrix | 四节点（1.21.6/26.1.2/26.2/26.3）编译绿+出 jar；1.21.6=mapped 工具链（现代 loom 插件 id+disableObfuscation=false），26.x=旧插件 id（loom 强制 unobf）；跨版本=buildSrc replacements（安全对）+ //? 交换（结构差异）；9 节点坐标已核实；**G-R3：1.18.2 不纳入矩阵**（MatrixStack 世代+无 MultiLineEditBox，02 §9），最低里程碑=1.21.6，1.19.4~1.21.4 为下一梯队 |
| T-200 | hud·Draw2D | S2 | **done** | session-agent | t200-t201-render | [报告](reports/T-200-T-201.md)；RenderPort+HudApi+FabricRenderPort（26.3 HudElementRegistry pipeline）；Draw2D 完整管线为渲染增量 |
| T-201 | render·Draw3D | S2 | **done** | session-agent | t200-t201-render | 合并交付（Draw3D 世界内渲染延后到渲染增量） |
| T-300 | 错误呈现链路 | S3 | **done**（v1） | session-agent | main | ErrorPresenter 节流+20 条环形历史（单测）；setErrorHandler 此前从未接线（运行时错误静默丢失）已修复；可点击聊天行 → /neomacros errors 查看器 |
| T-301 | screen·ScriptScreen | S3 | **done**（v1 查看器） | session-agent | main | 26.3 extractRenderState 管线 + MultiLineEditBox + Done；/neomacros open <script> 显示源码；编辑能力归 T-302 |
| T-302 | 编辑器 GUI | S3 | **done** | session-agent | t302-editor-panel | ScriptEditorScreen：MultiLineEditBox 可编辑 + Save/Save+Reload + 新脚本创建，保存即 modules.checkForChanges；/neomacros edit <script> 入口（路径规范化防逃逸） |
| T-303 | 管理面板 | S3 | **done** | session-agent | t302-editor-panel | ScriptManagerScreen：ObjectSelectionList 行列表（loaded/untrusted 标记、行点击进编辑器）+ New Script/Reload All/Trust Selected；修复 /neomacros trust <name> 无 handler 缺陷（pendingTrusts 映射） |
| T-310 | VitePress·npm types | S3 | **partially-done** | session-agent | t310-vitepress | [报告](reports/T-310.md)；VitePress 脚手架+npm types 打包脚本就位；npm install/build 待 Node.js 环境验证 |

## 需求方确认点（agent 不得自行决策）

| Gate | 触发 | 内容 |
|---|---|---|
| G-R1 | T-022 完成 | ESM PoC 结论确认 |
| G-R3 | T-106 期间 | 1.18.2 不可行时矩阵降级决策 |
| G0 | S0 出口 | 核心循环演示验收 |
| G1 | S1 出口 | API 面验收 |
| G2 | S2 出口 | 渲染验收 |
| G3 | S3 出口 | devkit 发布验收 |