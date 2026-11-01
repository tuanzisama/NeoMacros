异步订阅、一次性等待与可取消的同步处理器：

```js
const { events, chat } = neo;

const sub = events.on("player:damage", (p) => chat.log(`-${p.amount}`));
events.once("player:spawn", () => chat.log("首次生成"));
const sound = await events.waitFor("world:sound");

// 可取消事件必须用 onSync；返回 truthy 取消动作。
events.onSync("player:attackBlock", (p) => p.y < -60);
```
