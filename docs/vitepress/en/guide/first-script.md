# Your First Script

This page walks through a complete first script: printing to chat, reacting
to an event, and reloading without leaving the game.

## Create the file

Create `config/neomacros/scripts/hello.js`:

```js
export function onEvent(ev) { }

const { events, chat } = neo;

chat.log("Hello from NeoMacros!");

events.on("player:join", (payload) => {
    chat.log(`Welcome, ${payload.name}!`);
});
```

A script is a normal ES module. Two things matter here:

- `neo` is the global namespace holding every
  [facade](/en/api/): `chat`, `events`, `world`, `player`, and so on.
  Destructure the ones you use.
- `export function onEvent(ev) { }` is optional — NeoMacros recognizes it as
  the module entry point. Leaving it empty (or omitting it) is fine; the
  top-level code above is the actual program.
- An optional `meta` export names the script and declares its lifecycle and
  permissions (see [Permissions & Trust](./permissions-and-trust)):

  ```js
  export const meta = { name: "hello", lifecycle: "world" };
  ```

## Load it

Scripts with the default `world` lifecycle load when a world session opens
(use a `meta` export with `lifecycle: "always"` to load at game start
instead). To pick the file up without restarting, run:

```text
/neomacros reload
```

Then confirm it is live with `/neomacros list`. You should see the
`Hello from NeoMacros!` line in your local chat — `chat.log` renders locally
only, unlike `chat.say`, which sends a message to the server.

## React to events

The `events.on("player:join", ...)` line subscribes to the join event. The
handler runs on the script's own event loop, so it never blocks the game.
Each call returns a subscription handle; call `.deactivate()` on it (or
`events.off(handle)`) to unsubscribe.

Try jumping into a world — every join prints a welcome line.

## When things go wrong

If a script throws, NeoMacros captures the error and the script keeps
loading others. Open `/neomacros errors` to see the recent failures with
stack traces pointing back to your file.

## Next steps

- Go deeper on [events and async patterns](./events-and-async).
- See what happens when a script needs
  [extra permissions](./permissions-and-trust).
- Browse complete demos in the [Examples](/en/examples/) section.
