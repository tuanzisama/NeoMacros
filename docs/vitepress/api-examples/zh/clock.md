按游戏刻或真实时间等待，需要时跳到主线程：

```js
const { clock } = neo;

await clock.ticks(20);   // 一秒的游戏刻
await clock.sleep(500);  // 半秒真实时间
await clock.nextTick();  // 当前刻结束

const result = await clock.runOnMain(() => "在主线程上计算");
```
