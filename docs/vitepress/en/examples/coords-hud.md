# Coords HUD

A live coordinates overlay plus a marker beacon drawn in the world at your
position. Combines the `tick` event, the `hud` overlay, the live
[`player.self`](/en/api/player) handle and a `render` line.

```js
// coords-hud.js — coordinates overlay + world marker
export const meta = { name: "coords-hud", lifecycle: "world" };

const { events, hud, player, render } = neo;

const MARKER = 0xFF00FF00; // opaque green

events.on("tick", async ({ tick }) => {
    if (tick % 10 !== 0) return; // refresh twice a second

    const p = player.self;
    if (!p?.valid) {
        await hud.removeText("coords");
        render.clearLayer("marker");
        return;
    }

    const where = `${p.x.toFixed(1)} / ${p.y.toFixed(1)} / ${p.z.toFixed(1)}`;
    await hud.removeText("coords");
    await hud.addText("coords", where, 8, 8, 0xFFFFFFFF);

    render.clearLayer("marker");
    render.drawLine("marker",
        { x: p.x, y: p.y - 1, z: p.z },
        { x: p.x, y: p.y + 3, z: p.z },
        MARKER);
});
```

## Walkthrough

**Driving the loop.** There is no per-frame callback; instead the script
subscribes to `tick` and filters on the tick counter. `tick % 10` refreshes
the overlay twice per second — cheap, and plenty smooth for text.

**Reading live state.** `player.self` is a live handle: every property
access re-reads current state. `p?.valid` guards the menus — outside a
world the handle reads back as `undefined`, and optional chaining turns
that into a clean early return.

**Updating the overlay.** `hud.addText` registers a persistent entry keyed
by id; HUD entries do not auto-update, so the script removes and re-adds
the `coords` entry with fresh text each refresh. `hud.removeText` on a
missing id is a no-op, so the cleanup path can call it unconditionally.

**Drawing in the world.** `render.drawLine` draws a persistent world-space
line on a named layer. Shapes accumulate per layer until cleared, so the
script clears `marker` before every redraw. Everything is removed
automatically when the script unloads.

## Variations

- Show the dimension too: read [`world.dimension`](/en/api/world) in the
  same handler.
- Throttle less (`tick % 2`) for a snappier feel at negligible cost.
- Swap the line for a `render.drawBox` around the player's block.
