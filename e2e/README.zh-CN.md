[English](README.md) | 简体中文

# e2e

无需启动游戏的端到端测试。普通 JUnit 测试以 `StubAdapter`（[ports](../ports/) 接口的内存实现）启动真实运行时，喂入 `fixtures/scripts` 下的场景脚本，并对脚本观察到的结果做断言。

- `E2ERuntime` — 基于桩适配器启动 core + engine
- `TestBridge` — 让场景脚本把断言结果回传给测试
- `fixtures/scripts` — 场景脚本，与 [script-tests/cases](../script-tests/cases/) 互为镜像（CI 校验两份一致）

运行：`./gradlew :e2e:verifyE2EA`。
