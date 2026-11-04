# Your First Script

This page walks through a complete first script: a folder with a manifest,
a `startup` entry export, printing to chat, reacting to an event, and
reloading without leaving the game.

## Create the folder

Every script lives in its own folder below one of the three category
directories. The category decides what the script is for and where the GUI
and commands look for it:

| Folder | Category | Runs |
|---|---|---|
| `macros/keypress/<name>/` | key bindings | when the declared trigger edge fires (press / release / click) |
| `macros/event/<name>/` | event scripts | when an event declared in `config.event.on` fires |
| `macros/services/<name>/` | services | `startup` runs once on activation; the script keeps itself alive |

Create the manifest `config/neomacros/macros/event/hello/neomacro.yml`:

```yaml
name: hello            # unique ID: commands, trust table, enable state
version: 1.0.0
author: you            # name, version and author are required
description: says hi on world join

config:
  event:
    on: [world:join]   # event macros must declare the events they react to
```

Accepted formats are `neomacro.yml` and `neomacro.json`
(priority in that order when several exist). The top level carries identity
fields only: the required `name` / `version` / `author`, plus optional
`description`, `license`, `homepage`, `supportURL`, `main` (entry file,
default `index.js`) and `permissions` (see
[Permissions & Trust](./permissions-and-trust)).

**Behavior lives in the `config:` block**, grouped per type:

| Type | config section | Fields |
|---|---|---|
| keypress | `config.keypress` | `on` (`keydown`/`keyup`/`keypress`, default `keypress`), `default-key` (suggested initial binding), `modifiers` (required ctrl/shift/alt) |
| event | `config.event` | `on` (one or more event names, validated at load) |
| services | `config.service` (optional) | `lifecycle` (`always`/`world`/`manual`, default `always`), `autostart` (initial enable default) |

Legacy top-level behavior fields (`key`, `lifecycle`, `autostart`,
`events`) are gone — loading them fails with an error naming the migration
target; see the migration table at the bottom.

## Add the entry file

Create `config/neomacros/macros/event/hello/index.js`:

```js
const { chat } = neo;

export function startup(payload) {
    chat.log("Welcome back!");
}
```

A script is a normal ES module and **must export a `startup` function**
(`async` supported) — it is the entry NeoMacros invokes:

- **Event macros**: every occurrence of a `config.event.on` event calls
  `startup(payload)` with the event snapshot (the same payload `events.on`
  handlers receive).
- **Keypress macros**: every trigger edge calls `startup(ev)` with
  `ev = { event, key, code, clicks, modifiers }`.
- **Services**: one call on activation; long-running services keep
  themselves alive inside `startup` (a tick subscription, or an `async`
  loop with `clock.sleep`). The returned promise settles on the script's
  own event loop.

Top-level code still runs once at load and is a good place for constants
and module state; an optional `export function dispose() { }` does extra
cleanup on reload — subscriptions are removed automatically either way.

Manual subscriptions (`events.on` / `events.onSync` / `events.waitFor`,
wildcards and custom events included) keep working alongside the declared
ones.

## Load it

Scripts with the `world` lifecycle load when a world session opens (`always`
loads at game start — the default for services). To pick a new folder up
without restarting, run:

```text
/neomacros reload
```

Then confirm it is live with `/neomacros list`. The task manager (the
activity bar entry or the DevTools "tasks" tab in the web panel) now shows a
running task with its task ID, start reason, trigger count, and the player /
server snapshot.

## Bind a key (keypress scripts)

Scripts under `macros/keypress/` declare their trigger in
`config.keypress`:

```yaml
config:
  keypress:
    on: keydown           # keydown / keyup / keypress (default)
    default-key: key.keyboard.j
    modifiers: [ctrl]     # optional: require ctrl/shift/alt to be held
```

```js
export function startup(ev) {
    // ev = { event: 'keydown', key: 'key.keyboard.j', code: 74,
    //        clicks: 1, modifiers: ['ctrl'] }
    neo.chat.log(`pressed ${ev.key}`);
}
```

`default-key` is a **suggestion** only: bindings changed in the management
panel (`/neomacros panel`) or via `/neomacros keypress bind|unbind` are
stored in the central keymapping table and take priority over the
suggestion; rebinding never writes back to the manifest. `modifiers` match
leniently — the listed modifiers must be held, extra ones are ignored; keys
do not fire while a GUI (such as chat) is open, matching vanilla keybinds.

## React to events

The events declared in `config.event.on` are subscribed by the engine;
every occurrence calls `startup(payload)`, and subscriptions are removed
automatically on unload. Event names are validated at load: an unknown name
is rejected with the list of available events. Manual `events.on` handles
still support `.deactivate()` (or `events.off(handle)`).

Try jumping into a world — every join prints a welcome line.

## When things go wrong

If a manifest is invalid, the `config` block is missing or malformed, an
event name is unknown, or `startup` is missing, the folder is skipped and
the reason shows in `/neomacros errors` and the management panel. If a
script throws, NeoMacros captures the error and keeps loading others; open
`/neomacros errors` to see recent failures with stack traces pointing back
to your file.

## Migrating from the previous model

Scripts written against the old model (top-level program + `onKey`) fail to
load with a migration error. Rewrite them along this table:

| Old | New |
|---|---|
| top-level code as the program | move the logic into `export function startup(...)` (top-level stays fine for init) |
| `export function onKey(ev)` | `export function startup(ev)` + a `config.keypress` block declaring the edge |
| top-level `events.on("evt", ...)` | `config.event.on: [evt]` + `startup(payload)` (manual subscriptions still work) |
| top-level `key: ...` | `config.keypress.default-key` |
| top-level `lifecycle: ...` | `config.service.lifecycle` |
| top-level `autostart: ...` | `config.service.autostart` |
| top-level `events: [...]` (docs only) | `config.event.on` (now a real runtime subscription) |

## Next steps

- Go deeper on [events and async patterns](./events-and-async).
- See what happens when a script needs
  [extra permissions](./permissions-and-trust).
- Browse complete demos in the [Examples](/en/examples/) section.
