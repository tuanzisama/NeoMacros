Persistent text overlays on the HUD, updated by id:

```js
const { hud } = neo;

await hud.addText("coords", "0 / 64 / 0", 8, 8, 0xFFFFFFFF);
const ids = await hud.textIds(); // ["coords"]
await hud.removeText("coords");
```
