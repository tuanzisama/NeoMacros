# Block Guard

A toggleable guard that cancels attacks on a protected block type — a
practical look at keybinds and cancellable synchronous handlers.

```js
// block-guard.js — toggleable bedrock-style protection
export const meta = { name: "block-guard", lifecycle: "world" };

const { events, input, chat } = neo;

let enabled = false;

input.registerKeybind("key.keyboard.k", () => {
    enabled = !enabled;
    chat.actionbar(enabled ? "Block guard ON" : "Block guard OFF");
});

events.onSync("player:attackBlock", (payload) => {
    return enabled && payload.blockId === "minecraft:bedrock";
});
```

## Walkthrough

**The keybind.** `input.registerKeybind(keyName, sink)` registers a
persistent binding that shows up in the vanilla controls menu under the
NeoMacros category. Press <kbd>K</kbd> to toggle; the callback fires on
every press.

**The synchronous handler.** `player:attackBlock` is a cancellable event,
and cancellable events only accept synchronous subscriptions via
`events.onSync`. The callback runs on the dispatch thread, filter-style:
**return truthy to cancel the attack, falsy to let it through.**

**Staying short.** The handler does one comparison and returns. That is
the contract — sync callbacks block the dispatch thread while they run,
so no `await`, no loops, no heavy work. State checks like `enabled` are
exactly the right kind of work to do here.

**The payload.** `payload.blockId` is the registry id of the attacked
block, alongside its position. Guard multiple blocks with a `Set` of ids
and `payload.blockId` membership.

## Variations

- Guard a region instead: cancel when `payload.y` is below a threshold.
- Also protect interactions by subscribing to `player:interactBlock` the
  same way.
- Show what was cancelled: log inside the handler — but keep it to a short
  `chat.actionbar`, and remember it runs on the dispatch thread.
