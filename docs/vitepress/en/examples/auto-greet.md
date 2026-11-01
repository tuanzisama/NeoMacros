# Auto Greet

Log a line locally whenever a player joins. The smallest useful event
script — and a template for every other listener you will write.

```js
// macros/event/auto-greet/index.js — announce joins in your local chat
// manifest: macros/event/auto-greet/neomacro.yml — name: auto-greet, version: 1.0.0, author: you

const { events, chat } = neo;

const seen = new Set();

events.on("player:join", (payload) => {
    if (seen.has(payload.name)) return;
    seen.add(payload.name);
    chat.log(`o/ ${payload.name} joined`);
    chat.actionbar(`${payload.name} is here`);
});
```

## Walkthrough

**`meta` export.** The script names itself and uses the default `world`
lifecycle — loaded when a world session opens, unloaded when it closes. It
declares no permissions, so no trust prompt appears.

**The subscription.** `events.on("player:join", handler)` registers an
async listener on the script's own event loop. The payload carries the
player snapshot fields — `payload.name` is the joining player's name.

**The dedupe.** The `seen` set keeps repeat joins (dimension changes,
reconnects) from spamming the log. It resets when the script reloads,
which is usually what you want while iterating.

**Output channels.** `chat.log` renders in your local chat only — it never
reaches the server. `chat.actionbar` shows a temporary line above the
hotbar. Neither sends anything to other players.

## Variations

- React to leaves too: subscribe to `player:leave` the same way.
- Only care about yourself? Compare `payload.name` with
  [`player.self.name`](/en/api/player).
- Want a one-time welcome? Use `events.once` instead — it unsubscribes
  after the first firing.
