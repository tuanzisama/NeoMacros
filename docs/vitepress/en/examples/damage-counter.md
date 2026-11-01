# Damage Counter

Track how much damage you have taken — and keep the total across script
reloads and game restarts with [`storage`](/en/api/storage).

```js
// macros/event/damage-counter/index.js — persistent damage total
// manifest: macros/event/damage-counter/neomacro.yml — name: damage-counter, version: 1.0.0, author: you

const { events, storage, chat } = neo;

let total = storage.get("totalDamage") ?? 0;
chat.log(`Damage taken so far: ${total}`);

events.on("player:damage", (payload) => {
    total += payload.amount;
    storage.put("totalDamage", total);
    chat.actionbar(`Total: ${total.toFixed(1)}`);
});
```

## Walkthrough

**Loading persisted state.** `storage.get(key)` returns the stored value or
`null` when absent; `?? 0` turns a fresh install into a clean zero. The
value comes back as the same JSON type that was stored.

**Persisting on every hit.** `player:damage` fires with `amount` and the
resulting `health`. The handler adds it up and writes the total back with
`storage.put(key, value)`. Values must be JSON-serializable plain data —
numbers, strings, booleans, arrays, plain objects — which a number trivially
is.

**Where the data lives.** Each script owns an isolated store keyed by its
source file name. The store survives `/neomacros reload` and game
restarts, and is scoped to this script only — no other script can read or
clobber `totalDamage`.

**Resetting.** `storage.remove("totalDamage")` (or
`storage.put("totalDamage", null)`) wipes the counter; wire it to a
keybind from [`input`](/en/api/input) if you want a hotkey reset.

## Variations

- Track a session total too: keep a second in-memory variable that does
  not touch storage, and reset it on `player:join`.
- Store a small object instead — `storage.put("stats", { damage: total,
  hits: n })` — and grow it over time.
- Log a warning when `payload.health` drops below a threshold, using
  `chat.log`.
