# API Reference

Script-facing facades live under the global `neo` object. TypeScript
definitions ship as the generated `neomacros.d.ts`
([download](../../neomacros.d.ts), npm package `@neomacros/types`); the
per-module pages below are produced by the documentation pipeline in
`docs-builder` and carry a short example at the end of each page.

New to scripting? Start with the [Guide](/en/guide/getting-started), or
grab a complete script from the [Examples](/en/examples/) section.

## Modules

### Core

| Facade | Purpose |
|---|---|
| [`events`](/en/api/events) | subscribe / once / waitFor, synchronous cancellable handlers |
| [`thread`](/en/api/thread) | sleep, ticks, nextTick, runOnMain (root-level `clock.*` too) |
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
| [`inventory`](/en/api/player) | container access and item movement |

### System

| Facade | Purpose |
|---|---|
| [`fs`](/en/api/fs) | sandboxed file access (capability-gated) |
| [`net`](/en/api/net) | HTTP fetch (capability-gated) |
| [`java`](/en/api/java) | raw Java interop (permission-gated) |

### Input

| Facade | Purpose |
|---|---|
| [`input`](/en/api/input) | custom keybinds and scroll events |

## Classes and events

Every wrapper type (`Vec3`, `BlockPos`, `Block`, `BlockState`, `RayHit`,
`Player`, `LocalPlayer`, ...) and every subscribable event (`player:damage`,
`world:join`, ...) has its own page — browse them in the sidebar under
**Classes** and **Events**.

Cancellable events (`player:attackBlock`, `player:attackEntity`,
`player:interactBlock`, `player:interactEntity`) require
`events.onSync`; return truthy from the handler to cancel the game
action.
