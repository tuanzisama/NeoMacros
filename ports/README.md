English | [简体中文](README.zh-CN.md)

# ports

The boundary between the version-independent runtime and the game. Version-independent, compiled once and embedded into every product node.

- Port interfaces scripts reach through the API: `ChatPort`, `PlayerPort`, `EntityPort`, `WorldPort`, `InputPort`, `InventoryPort`, `RenderPort`, `Draw3DPort`, `LifecyclePort`
- `NeomacrosAdapter` — the SPI the product layer implements to expose these ports
- `model` — plain data types passed across the boundary
- `UnsupportedPortFeature` — thrown when an adapter lacks a feature on a given MC version

`src/testFixtures` contains architecture rules shared by other modules' tests.
