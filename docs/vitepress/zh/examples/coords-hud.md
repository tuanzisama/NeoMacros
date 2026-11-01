# 坐标 HUD

实时坐标叠加层，外加一根绘制在你位置上的世界内标记线。组合了 `tick`
事件、`hud` 叠加层、实时 [`player.self`](/zh/api/player) 句柄与 `render`
线段。

```js
// macros/event/coords-hud/index.js — 坐标叠加层 + 世界内标记
// manifest: macros/event/coords-hud/neomacro.yml — name: coords-hud, version: 1.0.0, author: you

const { events, hud, player, render } = neo;

const MARKER = 0xFF00FF00; // 不透明绿色

events.on("tick", async ({ tick }) => {
    if (tick % 10 !== 0) return; // 每秒刷新两次

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

## 逐段讲解

**驱动循环。** 没有逐帧回调；脚本订阅 `tick` 并按刻计数过滤。`tick % 10`
让叠加层每秒刷新两次——开销极低，对文字来说也足够顺滑。

**读取实时状态。** `player.self` 是实时句柄：每次属性访问都会重新读取
当前状态。`p?.valid` 守卫菜单场景——不在世界时句柄读回 `undefined`，可
选链把它变成一次干净的提前返回。

**更新叠加层。** `hud.addText` 按 id 注册持久文本条目；HUD 条目不会自动
更新，所以脚本每次刷新都先移除再重加 `coords` 条目。对不存在的 id 调用
`hud.removeText` 是无操作，清理路径可以放心调用。

**世界内绘制。** `render.drawLine` 在命名层上绘制持久的世界坐标线段。
图形在层内持续累积直到清除，所以每次重绘前先 `clearLayer("marker")`。
脚本卸载时一切都会自动移除。

## 变体

- 同时显示维度：在同一个处理器里读 [`world.dimension`](/zh/api/world)。
- 降低节流（`tick % 2`）换取更跟手的感觉，代价可忽略。
- 把线段换成围绕玩家方块的 `render.drawBox`。
