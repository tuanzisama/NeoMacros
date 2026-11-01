# Commands & Debugging

Everything about managing and troubleshooting scripts goes through the
`/neomacros` client command and the surfaces it opens.

## The management commands

| Command | Purpose |
|---|---|
| `/neomacros list` | show every loaded script, its lifecycle and status |
| `/neomacros reload` | rescan `config/neomacros/scripts` and apply changes |
| `/neomacros trust <script>` | grant a script its declared capabilities (see [Permissions & Trust](./permissions-and-trust)) |
| `/neomacros errors` | open the screen listing recent script failures |

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
