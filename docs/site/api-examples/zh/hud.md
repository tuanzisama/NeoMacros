HUD 上的持久文本条目，按 id 更新：

```js
const { hud } = neo;

await hud.addText("coords", "0 / 64 / 0", 8, 8, 0xFFFFFFFF);
const ids = await hud.textIds(); // ["coords"]
await hud.removeText("coords");
```
