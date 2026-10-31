# NeoMacros 任务看板

> **新会话/新 agent 从这里开始**（2026-10-31 建立交接入口）：
> 1. 读设计基线 `../../neomacros-docs/README.md`（已确认决策，不可单方面推翻）；
> 2. 读执行协议 `../../neomacros-docs/09-development-plan.md`（§0 Agent 使用说明 / DoD / 泳道 / E2E 规格）；
> 3. 在本看板认领任务：动工前标 `in-progress` + Owner/分支，完成后标 `done` 并写 `plan/reports/<ID>.md` 报告。
>
> 当前进度：**T-000 done**（commit `8560466` + 注释规范更正 `3b1aa9a`，报告 [reports/T-000.md](reports/T-000.md)）。
> 已解锁可并行认领：T-001 / T-010 / T-011 / T-024；T-022（GraalJS ESM PoC）允许提前开 spike 分支。
> 快速自验命令（仓库根）：`./gradlew build` → `bash scripts/smoke-build.sh`。

> 来源：[09-开发计划书](../../neomacros-docs/09-development-plan.md)。认领协议见计划书 §0：
> 动工前把任务标 `in-progress` 并填 Owner/分支/日期；完成后标 `done` 并在备注附报告链接。
> 同一任务同时只能有一个 owner；泳道内非标注可并行的任务不并行认领。

| ID | 任务 | 阶段 | 状态 | Owner | 分支 | 备注 |
|---|---|---|---|---|---|---|
| T-000 | 仓库骨架与构建脚手架 | S0 | **done** | session-agent | main | [报告](reports/T-000.md)；loom 1.19/Gradle 9.8 升级与 stonecutter 根脚本语义实证已回写架构文档 02 |
| T-001 | CI 工作流骨架 | S0 | **partially-done** | session-agent | t001-ci | [报告](reports/T-001.md)；workflow 全交付+CI 命令本地实证；actionlint 与 PR 实跑待配置 git remote 后闭环 |
| T-010 | doc-api 注解集 | S0 | **done** | session-agent | t010-doc-api | [报告](reports/T-010.md)；10 注解 + 18 单测绿 + maven 坐标已验证 |
| T-011 | ports 中立类型与最小端口集 | S0 | **done** | session-agent | t011-ports | [报告](reports/T-011.md)；7 record + 5 端口 + ArchUnit 共享守卫（testFixtures） |
| T-020 | core 事件框架 | S0 | pending | | | 依赖 T-010, T-011 |
| T-021 | core Runtime·线程·Engine SPI | S0 | pending | | | 依赖 T-020 |
| T-022 | engine GraalJS PoC ★R1 | S0 | pending | | | 依赖 T-021；spike 可提前 |
| T-023 | core ApiModel·绑定注册 | S0 | pending | | | 依赖 T-010, T-021 |
| T-024 | 文档管线骨架（docs-builder） | S0 | **done** | session-agent | t024-docs-builder | [报告](reports/T-024.md)；双 doclet + 快照回归 + tsc 实跑全绿；骨架期约定见其 README |
| T-030 | adapter/fabric 最小产品节点 | S0 | pending | | | 依赖 T-011, T-021 |
| T-031 | 模块管理器 | S0 | pending | | | 依赖 T-022, T-023 |
| T-032 | 配置系统 | S0 | pending | | | 依赖 T-021 |
| T-033 | storage 模块 | S0 | pending | | | 依赖 T-023, T-032 |
| T-040 | E2E-A 测试骨架 | S0 | pending | | | 依赖 T-031 |
| T-041 | E2E-G 套件骨架 | S0 | pending | | | 依赖 T-030, T-031 |
| T-100 | 权限信任门 | S1 | pending | | | 依赖 T-031, T-033 |
| T-101 | lane-world（world/entity/player） | S1 | pending | | | 依赖 T-030 |
| T-102 | lane-inventory | S1 | pending | | | 依赖 T-030 |
| T-103 | lane-input | S1 | pending | | | 依赖 T-030 |
| T-104 | lane-fs/net | S1 | pending | | | 依赖 T-100 |
| T-105 | lane-command | S1 | pending | | | 依赖 T-100 |
| T-106 | 全节点矩阵 + 1.18.2 可行性 ★R3 | S1 | pending | | | 依赖 T-101~105 合入；★需求方确认点 |
| T-200 | hud·Draw2D | S2 | pending | | | 依赖 T-106 |
| T-201 | render·Draw3D | S2 | pending | | | 依赖 T-106 |
| T-300 | 错误呈现链路 | S3 | pending | | | 依赖 T-106 |
| T-301 | screen·ScriptScreen | S3 | pending | | | 依赖 T-106 |
| T-302 | 编辑器 GUI | S3 | pending | | | 依赖 T-301 |
| T-303 | 管理面板 | S3 | pending | | | 依赖 T-031, T-301 |
| T-310 | VitePress·npm types | S3 | pending | | | 依赖 T-024 |

## 需求方确认点（agent 不得自行决策）

| Gate | 触发 | 内容 |
|---|---|---|
| G-R1 | T-022 完成 | ESM PoC 结论确认 |
| G-R3 | T-106 期间 | 1.18.2 不可行时矩阵降级决策 |
| G0 | S0 出口 | 核心循环演示验收 |
| G1 | S1 出口 | API 面验收 |
| G2 | S2 出口 | 渲染验收 |
| G3 | S3 出口 | devkit 发布验收 |
