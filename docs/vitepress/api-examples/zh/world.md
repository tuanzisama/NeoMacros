查询周围已加载的世界：

```js
const { world, chat } = neo;

const block = world.blockAt(0, 64, 0);         // 已加载区块外为 null
const hit = world.raycast(0, 70, 0, 0, 40, 0); // 射线上第一个实心方块

chat.log(`${world.dimension} · 时间 ${world.time} · 降雨：${world.raining}`);
```
