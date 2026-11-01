English | [简体中文](README.zh-CN.md)

# e2e

End-to-end tests that run scripts without launching the game. A plain JUnit test boots the real runtime with a `StubAdapter` (an in-memory implementation of the [ports](../ports/) interfaces), feeds it the scenario scripts from `fixtures/scripts`, and asserts on what the scripts observed.

- `E2ERuntime` — boots core + engine against the stub adapter
- `TestBridge` — lets scenario scripts report assertions back to the test
- `fixtures/scripts` — scenario scripts, mirrored from [script-tests/cases](../script-tests/cases/) (CI checks the two copies stay identical)

Run with `./gradlew :e2e:verifyE2EA`.
