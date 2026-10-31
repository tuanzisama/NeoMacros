# API Reference

Script-facing facades live under the global `neo` object. TypeScript
definitions ship as the generated `neomacros.d.ts` (npm package
`@neomacros/types`); the full per-module pages are produced by the
documentation pipeline in `docs-builder`.

| Facade | Purpose |
|---|---|
| `neo.events` | subscribe / once / waitFor, synchronous cancellable handlers |
| `neo.clock` | sleep, ticks, nextTick, runOnMain (root-level `clock.*` too) |
| `neo.chat` | say, log, actionbar |
| `neo.storage` | per-script key-value persistence |
| `neo.world` | time, dimension, weather, block queries |
| `neo.entity` | entity lookup by UUID / radius |
| `neo.player` | local player snapshot and status effects |
| `neo.fs` | sandboxed file access (capability-gated) |
| `neo.net` | HTTP fetch (capability-gated) |
| `neo.input` | custom keybinds and scroll events |
| `neo.command` | register client commands |
| `neo.hud` | persistent HUD text overlays |
| `neo.render` | world-space wireframe boxes and lines |

## Event catalog

All built-in events carry plain payload objects. Highlights:

| Event | Payload fields |
|---|---|
| `tick` | `tick` |
| `player:join` / `player:leave` / `player:spawn` | player snapshot fields |
| `player:damage` / `player:heal` | `amount`, `health` |
| `player:effect` | `effectId`, `added`, `amplifier` |
| `player:attackBlock` (cancellable) | `blockId`, position |
| `world:blockUpdate` | `x`, `y`, `z`, `blockId` |
| `world:chunkLoad` / `world:chunkUnload` | `chunkX`, `chunkZ` |
| `world:sound` | `soundId`, position, `volume`, `pitch` |
| `world:bossbar` | `uuid`, `action`, `name`, `progress` |
| `entity:damage` / `entity:heal` | `entity`, `amount` |
| `entity:load` / `entity:unload` | entity identity fields |

Cancellable events (`player:attackBlock`, `player:attackEntity`,
`player:interactBlock`, `player:interactEntity`) require
`events.onSync`; return truthy from the handler to cancel the game
action.
