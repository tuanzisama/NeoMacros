[English](README.md) | 简体中文

# script-tests

在真实游戏客户端内运行的端到端用例。每个条目都是一个脚本文件夹（清单 + 入口），遵循标准宏目录布局：

- `suite/` — E2E-G 运行器（`e2e-suite`，lifecycle `always`）；安装到 `config/neomacros/macros/services/` 下。
- `cases/` — 独立用例；事件驱动的装到 `config/neomacros/macros/event/` 下。

运行器逐个执行用例，每个用例打印一行结果（`[NeoMacros-E2E] <caseId> PASS|FAIL <detail>`），最后输出 `SUITE-DONE` 标记，并通过 storage 持久化完整结果——客户端退出后由 `verifyGameE2E` 协议读取。加 `-Dneomacros.e2e.quitOnDone=true` 时，套件跑完模组会自动关闭客户端。

用例契约：只使用公开脚本 API，在一次性状态下无害运行，失败必须留下 `detail`。需要人工判断的用例会打印观测结果供人工核对。
