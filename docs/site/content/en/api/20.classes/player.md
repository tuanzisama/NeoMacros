# Player

Handle · Wraps: `net.minecraft.world.entity.player.Player` · Extends: [`LivingEntity`](livingentity) · Extended by: [`LocalPlayer`](localplayer)

Live player handle: the entity read face comes through the entity port (works for remote players too); the local-only extras (hunger, air, gamemode, xp) read through the player port and are undefined for remote players.

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `air` | `number` | The air (breath) value; only the local player carries it. (read-only) |
| `gamemode` | `string` | The gamemode identifier (survival/creative/adventure/ spectator); only the local player carries it. (read-only) |
| `health` | `number` | The health value; the local player reads through the player port (its vitals live on the player snapshot), a remote player falls back to the entity snapshot. (read-only) |
| `hunger` | `number` | The hunger value; only the local player carries it. (read-only) |
| `inventory` | [PlayerInventory](playerinventory) | The player's own inventory; only the local player carries it. (read-only) |
| `saturation` | `number` | The food saturation value; only the local player carries it. (read-only) |
| `xp` | `Record<string, unknown>` | The experience as {level, progress, total}; only the local player carries it. (read-only) |

## Methods

### `snapshot()`

Returns a point-in-time snapshot copy, or null when the player is not loaded.

**Returns** `EntitySnapshot`

Since: 0.1.0 · MC: * · Loader: *

### `toJSON()`

JSON form; the local player renders through the player view.

**Returns** `Record<string, unknown>`

Since: 0.1.0 · MC: * · Loader: *

### `toString()`

Human-readable form.

**Returns** `string`


## Examples

The local player is a live handle; take a snapshot for a stable copy:

```js
const { player, chat } = neo;

const p = player.self;
if (p?.valid) {
    chat.log(`${p.name} at ${p.x}, ${p.y}, ${p.z} with ${p.health} HP`);
    const snapshot = p.snapshot(); // point-in-time copy
}
```
