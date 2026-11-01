The local player is a live handle; take a snapshot for a stable copy:

```js
const { player, chat } = neo;

const p = player.self;
if (p?.valid) {
    chat.log(`${p.name} at ${p.x}, ${p.y}, ${p.z} with ${p.health} HP`);
    const snapshot = p.snapshot(); // point-in-time copy
}
```
