English | [简体中文](README.zh-CN.md)

# NeoMacros

ECMAScript scripting for the Minecraft client, Fabric only. Scripts can hook chat, entities, input, inventory, rendering, HUD, sound and world events.

Multiple Minecraft versions are built from one codebase with [Stonecutter](https://stonecutter.kikugie.dev/) — one virtual node per target version (`1.21.6`, `26.1.2`, `26.2`, `26.3`).

## MCP agent server

An embedded MCP (Model Context Protocol) server lets AI agents read logs and game state or drive the client over Streamable HTTP. It is off by default; start it in-game and print the connection details:

```
/neomacros mcp start
```

Then register it with your agent, e.g. Claude Code:

```bash
claude mcp add --transport http neomacros http://127.0.0.1:30637/mcp \
  --header "Authorization: Bearer <token from the command>"
```

Tools cover logs and diagnostics, game state (player, inventory, screen, world), control (script start/stop, chat, commands, keys), and a JavaScript eval. The server binds the loopback, authenticates with a bearer token, and every tool group can be disabled in the config — see the [MCP guide](https://neomacros.tuanzi.ink/en/guide/mcp-server).

## Layout

| Module | Purpose |
| --- | --- |
| [core](core/) | Version-independent script runtime: lifecycle, events, config, storage, security |
| [engine](engine/) | GraalJS engine integration with a sandboxed module filesystem |
| [ports](ports/) | Version-independent port interfaces and the adapter SPI |
| [product](product/) | Fabric adapter and mod entrypoint; one Stonecutter node per MC version |
| [doc-api](doc-api/) | Annotations marking the public script API surface |
| [e2e](e2e/) | Headless end-to-end harness, runs scripts without the game |
| [script-tests](script-tests/) | In-game E2E case suite, shared with the e2e fixtures |

The documentation site and its generator live in the separate
[NeoMacros-Doc](https://github.com/tuanzisama/NeoMacros-Doc)
repository; API-source pushes here trigger its regeneration through the
`docs-dispatch` workflow.

## Build

```bash
./gradlew build           # all product nodes
./gradlew :product:build  # active node only
```

Local builds land on the `local` channel
(`neomacros-26.3-fabric-0.1.0-local.jar`); CI stamps dev/canary/release
channels with the same env contract. Channel naming, triggers and the
update-check data flow are documented in
[docs/releasing.md](docs/releasing.md).

## License

MIT
