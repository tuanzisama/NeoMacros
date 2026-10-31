# script-tests —— 游戏内 E2E 套件（E2E-G）

架构依据：[09-开发计划书 §7.2](../../neomacros-docs/09-development-plan.md)。

- `cases/` 与 `e2e/fixtures/scripts/` **同一份文件**（T-040 起同步，CI hash 校验）；
- runner `suite.js` 由 T-041 落地；当前用例以头部注释声明的【手动探针】流程执行；
- 结果协议（T-041 起）：`e2e-results.json` + 日志标记 `[NeoMacros-E2E] <caseId> PASS|FAIL <detail>`；
- 契约：只用公开 API、一次性状态下无害运行、失败必须留 detail；`observe` 用例的人工比对结论须写入 `plan/reports/` 执行报告。
