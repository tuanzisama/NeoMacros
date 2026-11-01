# Your First Script

This page walks through a complete first script: a folder with a manifest,
printing to chat, reacting to an event, and reloading without leaving the
game.

## Create the folder

Every script lives in its own folder below one of the three category
directories. The category decides what the script is for and where the GUI
and commands look for it:

| Folder | Category | Runs |
|---|---|---|
| `macros/keypress/<name>/` | key bindings | when its bound key is pressed |
| `macros/event/<name>/` | event scripts | when the events it subscribes to fire |
| `macros/services/<name>/` | services | from game start until quit (start/stop via panel or command) |

Create the manifest `config/neomacros/macros/event/hello/neomacro.yml`:

```yaml
name: hello            # unique ID: commands, trust table, enable state
version: 1.0.0
author: you            # name, version and author are required
description: says hi on world join
```

Accepted formats are `neomacro.yml`, `neomacro.json` and `neomacro.json5`
(priority in that order when several exist). Optional fields include
`description`, `license`, `homepage`, `supportURL`, `main` (entry file,
default `index.js`), `key` (default key binding for keypress scripts),
`lifecycle` (`always` / `world` / `manual`; the category default is `always`
for services and `world` otherwise) and `permissions` (see
[Permissions & Trust](./permissions-and-trust)).

## Add the entry file

Create `config/neomacros/macros/event/hello/index.js`:

```js
const { events, chat } = neo;

chat.log("Hello from NeoMacros!");

events.on("world:join", (payload) => {
    chat.log("Welcome back!");
});
```

A script is a normal ES module. Two things matter here:

- `neo` is the global namespace holding every
  [facade](/en/api/): `chat`, `events`, `world`, `player`, and so on.
  Destructure the ones you use.
- The top-level code is the actual program; it runs once when the script
  loads. An optional `export function dispose() { }` does extra cleanup on
  reload — subscriptions are removed automatically either way.

## Load it

Scripts with the `world` lifecycle load when a world session opens (`always`
loads at game start — the default for services). To pick a new folder up
without restarting, run:

```text
/neomacros reload
```

Then confirm it is live with `/neomacros list`. You should see the
`Hello from NeoMacros!` line in your local chat — `chat.log` renders locally
only, unlike `chat.say`, which sends a message to the server.

## Bind a key (keypress scripts)

Scripts under `macros/keypress/` declare a default key in the manifest
(`key: key.keyboard.j`) and export the handler the key press calls:

```js
export function onKey(ev) {
    neo.chat.log(`pressed ${ev.key}`);
}
```

Bindings changed in the management panel (`/neomacros panel`) or via
`/neomacros keypress bind|unbind` are stored in the central binding table
and take priority over the manifest default.

## React to events

The `events.on("world:join", ...)` line subscribes to the join event. The
handler runs on the script's own event loop, so it never blocks the game.
Each call returns a subscription handle; call `.deactivate()` on it (or
`events.off(handle)`) to unsubscribe.

Try jumping into a world — every join prints a welcome line.

## When things go wrong

If a manifest is invalid or the entry file is missing, the folder is skipped
and the reason shows in `/neomacros errors` and the management panel. If a
script throws, NeoMacros captures the error and keeps loading others; open
`/neomacros errors` to see recent failures with stack traces pointing back
to your file.

## Next steps

- Go deeper on [events and async patterns](./events-and-async).
- See what happens when a script needs
  [extra permissions](./permissions-and-trust).
- Browse complete demos in the [Examples](/en/examples/) section.
