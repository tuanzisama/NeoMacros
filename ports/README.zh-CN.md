[English](README.md) | 简体中文

# ports

版本无关运行时与游戏之间的边界。版本无关，只编译一次，嵌入每个 product 节点。

- 脚本经由 API 触达的端口接口：`ChatPort`、`PlayerPort`、`EntityPort`、`WorldPort`、`InputPort`、`InventoryPort`、`RenderPort`、`Draw3DPort`、`LifecyclePort`
- `NeomacrosAdapter` — product 层实现的 SPI，用于暴露上述端口
- `model` — 跨边界传递的纯数据类型
- `UnsupportedPortFeature` — 适配器在当前 MC 版本不具备某特性时抛出

`src/testFixtures` 提供供其他模块测试共用的架构规则。
