/**
 * E2E-G 用例: T-000 bootstrap（模式: auto）
 * ============================================================
 * ⚠ 当前为占位契约：脚本运行时（engine, T-022）与测试 harness（T-041）
 *   尚未落地，本用例只能以【手动探针】方式执行：
 *
 *   1. `./gradlew runFabricClient` 启动到主菜单；
 *   2. 日志（run/logs/latest.log）应出现: [neomacros] NeoMacros bootstrap (T-000 skeleton)；
 *   3. 聊天输入 /neomacros → 期望回复: "NeoMacros skeleton (T-000) is loaded."
 *
 * __test 桥接契约（为 T-041 预留，届时启用）:
 *   __test.report('T000-bootstrap', ok, detail)
 * 断言内容: /neomacros 已注册（命令反馈文本匹配）且 bootstrap 标记已在日志出现。
 */
// TODO(T-041): 接入 __test 桥接后替换为自动断言，移除本注释块。
