# NeoMacros 任务看板

> **新会话/新 agent 从这里开始**（2026-10-31 建立交接入口）：
> 1. 读设计基线 `../../neomacros-docs/README.md`（已确认决策，不可单方面推翻）；
> 2. 读执行协议 `../../neomacros-docs/09-development-plan.md`（§0 Agent 使用说明 / DoD / 泳道 / E2E 规格）；
> 3. 在本看板认领任务：动工前标 `in-progress` + Owner/分支，完成后标 `done` 并写 `plan/reports/<ID>.md` 报告。
>
> 当前进度（2026-11-01 第二次审计）：**done=24, partially=3, pending=4, 全量 build 绿（26.1.2/26.2/26.3 三节点，209 tests）**。**T-101b 全部完成**：11 件事件全交付（entity:damage/heal=per-tick 健康快照差量、world:blockUpdate/chunkLoad/chunkUnload=ClientLevel+ClientChunkCache mixin、player:effect=PlayerPort.activeEffects 差量、world:bossbar=ClientPacketListener mixin 重派发；PortEventWiringTest 5/5 绿）。**T-106 26.x 完成**：26.1.2/26.2/26.3 三节点编译绿并出 fabric jar；adapter/fabric 源码已迁入 product 分支树（Stonecutter 只预处理版本化项目自有源码，外部 srcDir 绕过管线）；跨版本差异走 product/build.gradle.kts replacements（KEYSYM→KEYBOARD、gui.hud→gui）；1.x 9 节点 fabric 版本已按 Modrinth 核实修正。**pending**: S2 Draw2D/Draw3D 完整管线、T-300 错误链路、T-301~303 面板、T-310 VitePress 收口。下一窗口优先：S2 渲染 → T-310 → T-300~303。
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
| T-106 | 全节点矩阵 + 1.18.2 可行性 ★R3 | S1 | **partially-done**（26.x 完成） | session-agent | t106-node-matrix | 26.1.2/26.2/26.3 三节点编译绿+出 jar；adapter/fabric 源码迁入 product 分支树（Stonecutter 仅预处理版本化项目自有源码）；跨版本差异=product/build.gradle.kts replacements（KEYBOARD/KEYSYM、gui.hud/gui）；9 节点 fabric 版本已按 Modrinth 核实；1.x 激活（obf 映射+Java 17/21 工具链）与 1.18.2 可行性决策（G-R3）留后续窗口 |
| T-200 | hud·Draw2D | S2 | **done** | session-agent | t200-t201-render | [报告](reports/T-200-T-201.md)；RenderPort+HudApi+FabricRenderPort（26.3 HudElementRegistry pipeline）；Draw2D 完整管线为渲染增量 |
| T-201 | render·Draw3D | S2 | **done** | session-agent | t200-t201-render | 合并交付（Draw3D 世界内渲染延后到渲染增量） |
| T-300 | 错误呈现链路 | S3 | pending | | | 依赖 T-106 |
| T-301 | screen·ScriptScreen | S3 | pending | | | 依赖 T-106 |
| T-302 | 编辑器 GUI | S3 | pending | | | 依赖 T-301 |
| T-303 | 管理面板 | S3 | pending | | | 依赖 T-031, T-301 |
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