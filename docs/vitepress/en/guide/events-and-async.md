# Events & Async

NeoMacros scripts are event-driven: the game fires events, your handlers
react, and everything else is `async`/`await` on the script's own event
loop. This page covers the full subscription toolkit from the
[`events`](/en/api/events) and [`clock`](/en/api/clock) facades.

## Subscribing: `events.on`

`events.on(name, handler)` registers an asynchronous listener. The handler
runs on the script context's event loop and receives the event payload as a
plain object:

```js
const { events, chat } = neo;

const handle = events.on("player:damage", (payload) => {
    chat.actionbar(`Ouch! -${payload.amount.toFixed(1)} HP`);
});
```

The returned handle is opaque; two ways to unsubscribe:

```js
handle.deactivate();   // on the subscription object
events.off(handle);    // or through the facade, by handle…
events.off("player:damage");  // …or by event name
```

Subscriptions created while a module is loading are attributed to that
module and auto-unsubscribed when the module unloads, so reloading never
leaks handlers.

## Wildcards

Event names support `domain:*` and bare `*` patterns:

```js
events.on("world:*", (payload) => chat.log(`something happened in the world`));
```

## One-shot and promise styles

- `events.once(name, handler)` — subscribes for exactly one firing and
  auto-unsubscribes.
- `events.waitFor(name)` — returns a promise that settles with the next
  payload.
- `events.waitForUntil(name, filter, timeoutMillis)` — waits for an
  occurrence matching `filter`, rejecting after the timeout; the filter
  runs on the dispatch thread, so keep it short.

`waitFor` composes with `async`/`await`:

```js
const { events, clock, chat } = neo;

async function watchDamage() {
    while (true) {
        const ev = await events.waitFor("player:damage");
        chat.actionbar(`Ouch! -${ev.amount.toFixed(1)} HP`);
        await clock.ticks(20);
    }
}
watchDamage();
```

## Synchronous handlers: `events.onSync`

A few events are **cancellable** (`player:attackBlock`, `player:attackEntity`,
`player:interactBlock`, `player:interactEntity`). Only these accept
synchronous subscriptions, made with `events.onSync`. The callback runs on
the dispatch thread and works filter-style, like `Array.filter`: return a
truthy value to cancel the game action, falsy to let it through.

```js
const { events } = neo;

// Cancel every attempt to attack a block in a protected dimension area.
events.onSync("player:attackBlock", (payload) => {
    return payload.y < -60;   // truthy → cancel
});
```

Sync callbacks must stay short: they block the dispatch thread while they
run. No `await`, no long loops — decide and return.

## Timing with `clock`

Every `clock` function is promise-returning and bound to the calling
script's event loop:

| Call | Resolves |
|---|---|
| `clock.nextTick()` | at the end of the current client tick |
| `clock.ticks(count)` | after `count` client ticks |
| `clock.sleep(millis)` | after a real-time delay, independent of ticks |
| `clock.runOnMain(task)` | after running `task` on the game main thread; settles with its return value |

`clock.runOnMain` is the bridge when you need to touch main-thread-only
state from an async handler.

## Cancelation and cleanup

Long-running async loops (like `watchDamage` above) keep running as long as
the script is loaded — `/neomacros reload` tears them down with the module.
For anything you want to stop earlier, keep a flag or a
`clock.sleep`-based deadline rather than relying on unhandled promise
cancelation.

## Next steps

- Scripts that touch files or the network need
  [permissions and trust](./permissions-and-trust).
- The full event catalog lives in the
  [API reference](/en/api/).
