# API Reference

Script-facing facades live under the global `neo` object. TypeScript
definitions ship as the generated `neomacros.d.ts`
([download](/neomacros.d.ts), npm package `@neomacros/types`); the
per-module pages below are produced by the documentation pipeline in
`docs-builder` and carry a short example at the end of each page.

New to scripting? Start with the [Guide](/en/guide/getting-started), or
grab a complete script from the [Examples](/en/examples/) section.

## Modules

### Core

| Facade | Purpose |
|---|---|
| [`events`](/en/api/events) | subscribe / once / waitFor, synchronous cancellable handlers |
| [`clock`](/en/api/clock) | sleep, ticks, nextTick, runOnMain (root-level `clock.*` too) |
| [`storage`](/en/api/storage) | per-script key-value persistence |

### UI

| Facade | Purpose |
|---|---|
| [`chat`](/en/api/chat) | say, log, actionbar |
| [`hud`](/en/api/hud) | persistent HUD text overlays |
| [`command`](/en/api/command) | register client commands |
| [`render`](/en/api/render) | world-space wireframe boxes and lines |

### World

| Facade | Purpose |
|---|---|
| [`world`](/en/api/world) | time, dimension, weather, block queries |
| [`player`](/en/api/player) | local player snapshot and status effects |
| [`entity`](/en/api/entity) | entity lookup by UUID / radius |

### System

| Facade | Purpose |
|---|---|
| [`fs`](/en/api/fs) | sandboxed file access (capability-gated) |
| [`net`](/en/api/net) | HTTP fetch (capability-gated) |

### Input

| Facade | Purpose |
|---|---|
| [`input`](/en/api/input) | custom keybinds and scroll events |

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
