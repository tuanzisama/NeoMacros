Query the loaded world around you:

```js
const { world, chat } = neo;

const block = world.blockAt(0, 64, 0);         // null outside loaded chunks
const hit = world.raycast(0, 70, 0, 0, 40, 0); // first solid block on the ray

chat.log(`${world.dimension} · time ${world.time} · raining: ${world.raining}`);
```
