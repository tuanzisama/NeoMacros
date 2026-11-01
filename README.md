English | [简体中文](README.zh-CN.md)

# NeoMacros

ECMAScript scripting for the Minecraft client, Fabric only. Scripts can hook chat, entities, input, inventory, rendering, HUD, sound and world events.

Multiple Minecraft versions are built from one codebase with [Stonecutter](https://stonecutter.kikugie.dev/) — one virtual node per target version (`1.21.6`, `26.1.2`, `26.2`, `26.3`).

## Layout

| Module | Purpose |
| --- | --- |
| [core](core/) | Version-independent script runtime: lifecycle, events, config, storage, security |
| [engine](engine/) | GraalJS engine integration with a sandboxed module filesystem |
| [ports](ports/) | Version-independent port interfaces and the adapter SPI |
| [product](product/) | Fabric adapter and mod entrypoint; one Stonecutter node per MC version |
| [doc-api](doc-api/) | Annotations marking the public script API surface |
| [docs-builder](docs-builder/) | Standalone build generating API docs and TypeScript definitions |
| [docs](docs/) | VitePress documentation site (English / Chinese) |
| [e2e](e2e/) | Headless end-to-end harness, runs scripts without the game |
| [script-tests](script-tests/) | In-game E2E case suite, shared with the e2e fixtures |

## Build

```bash
./gradlew build                  # all product nodes
./gradlew :product:build         # active node only
./gradlew -p docs-builder check  # API docs pipeline (see its README)
```

## License

MIT
