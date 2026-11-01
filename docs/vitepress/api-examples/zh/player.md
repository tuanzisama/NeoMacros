本地玩家是实时句柄；需要稳定副本时取一份快照：

```js
const { player, chat } = neo;

const p = player.self;
if (p?.valid) {
    chat.log(`${p.name} 位于 ${p.x}, ${p.y}, ${p.z}，血量 ${p.health}`);
    const snapshot = p.snapshot(); // 时间点快照
}
```
