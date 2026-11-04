# Commands & Debugging

Everything about managing and troubleshooting scripts goes through the
`/neomacros` client command and the surfaces it opens.

## The management commands

| Command | Purpose |
|---|---|
| `/neomacros list [category]` | show discovered scripts per category with state |
| `/neomacros reload` | rescan `config/neomacros/macros` and apply changes |
| `/neomacros keypress bind <script> <key>` | bind a `keypress/` script to a key (central table, overrides the manifest) |
| `/neomacros keypress unbind <script>` | remove the binding (persists across restarts) |
| `/neomacros service start/stop/restart <script>` | manually control a `services/` script |
| `/neomacros trust <script>` | grant a script its declared capabilities (see [Permissions & Trust](./permissions-and-trust)) |
| `/neomacros edit <script>` / `open <script>` | open the editor / source viewer |
| `/neomacros panel` | open the management panel (groups scripts by category) |
| `/neomacros errors` | open the screen listing recent script failures and invalid folders |

The usual edit cycle is: save the file → `/neomacros reload` → observe.
Reloading unloads the old module (its subscriptions and HUD/render layers go
with it) and loads the new content fresh.

## Logging from a script

Use [`chat`](/en/api/chat) for output while developing:

```js
const { chat } = neo;

chat.log("renders in your local chat only");       // local chat line
chat.actionbar("above the hotbar, fades out");     // action bar
```

`chat.log` is local-only — it never reaches the server — so it is safe for
verbose debugging.

## The script console

Macros also get the browser-style **`console`** global. Output streams live
into the **控制台 (Console)** tab of the web panel DevTools, tagged with the
macro name, file, and line (click the location to jump to that line in the
editor), and a plain-text copy mirrors into the game log:

```js
console.log("hello", { a: 1 }, [1, 2, 3]);   // objects/arrays expand in the panel
console.info("info");  console.warn("warn"); console.error("error"); console.debug("debug");
console.count("hits");                        // hits: 1 / 2 / …
console.time("boot");  console.timeEnd("boot");  // boot: 12.34ms
console.group("ctx");  console.log("nested"); console.groupEnd();
console.trace("who called");                  // expandable call stack
console.table([{ item: "apple", n: 3 }]);     // rendered as a table
console.assert(cond, "only when cond is falsy");
console.clear();                              // clears the panel view
```

Notes:

- **Structured rendering**: objects and arrays are expandable preview trees
  (depth/length capped); MC `Component`s render as colored rich text; Java
  `Class`es show their fully-qualified name.
- **Placeholders**: `console.log("player %s level %d", name, level)` supports
  `%s %d %i %f %j %o %c`.
- **Rate limiting**: 50-burst, 25/s sustained per script; excess emissions are
  dropped and surfaced as one WARN notice ("N message(s) suppressed").
- `console` needs no `meta.permissions` entry — every script has it.

## Reading the errors screen

When a handler throws, NeoMacros captures the error; the script does not
take the game down with it. `/neomacros errors` lists the recent failures
with the script name, message and stack trace pointing back to your file.
Common entries:

- **`... is not a function`** — a typo in a facade call, or a member used
  before its module finished loading. Check the spelling against the
  [API reference](/en/api/).
- **Permission denied** — the script called a guarded member (`fs`, `net`,
  `command`) without being trusted. Approve via the prompt or
  `/neomacros trust`.
- **undefined reads from handles** — `player.self`, `entity.lookup(...)` and
  friends read back as `undefined` outside a world (or once an entity
  vanished). Guard with `valid` checks instead of assuming presence.

## Common pitfalls

- **Cancellable events need `onSync`.** `events.on` handlers cannot cancel
  `player:attackBlock` and friends; only `events.onSync` accepts them, and
  its callback must stay short because it blocks the dispatch thread.
- **Storage values are JSON data.** [`storage.put`](/en/api/storage) accepts
  plain objects, arrays, strings, numbers, booleans — not class instances,
  maps or functions. Passing `null` deletes the key.
- **`fs` paths are relative to the game directory.** Absolute paths and
  traversal above the game directory raise; there is no escaping the
  sandbox.
- **Async handlers are async.** A handler that forgets `await` will not wait
  for its work — and errors inside a fire-and-forget promise surface in the
  errors screen rather than at the call site.

## Next steps

- Full command and event listings: [API reference](/en/api/).
- Working demos under real conditions: [Examples](/en/examples/).
