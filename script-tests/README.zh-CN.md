[English](README.md) | 简体中文

# script-tests

在真实游戏客户端内运行的端到端用例。`suite.js` 是运行器：逐个执行用例，每个用例打印一行结果（`[NeoMacros-E2E] <caseId> PASS|FAIL <detail>`），最后输出 `SUITE-DONE` 标记，并通过 storage 持久化完整结果——客户端退出后由 `verifyGameE2E` Gradle 任务读取。

`cases/` 会镜像到 [e2e/fixtures/scripts](../e2e/fixtures/scripts/)（同一份文件，CI 做哈希校验）。

用例契约：只使用公开脚本 API，在一次性状态下无害运行，失败必须留下 `detail`。需要人工判断的用例会打印观测结果供人工核对。
