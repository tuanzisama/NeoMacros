# 事件与异步

NeoMacros 脚本是事件驱动的：游戏发出事件，你的处理器响应，其余一切都
是脚本自身事件循环上的 `async`/`await`。本页覆盖
[`events`](/zh/api/events) 与 [`clock`](/zh/api/clock) 两个门面的完整订
阅工具箱。

## 订阅：`events.on`

`events.on(name, handler)` 注册异步监听。处理器运行在脚本上下文的事件
循环上，收到的负载是普通对象：

```js
const { events, chat } = neo;

const handle = events.on("player:damage", (payload) => {
    chat.actionbar(`哎哟！-${payload.amount.toFixed(1)} HP`);
});
```

返回的句柄是不透明对象，两种取消方式：

```js
handle.deactivate();          // 在订阅对象上直接取消
events.off(handle);           // 或通过门面按句柄取消……
events.off("player:damage");  // ……或按事件名取消
```

模块加载期间创建的订阅会归属到该模块，模块卸载时自动取消——热重载不
会泄漏处理器。

## 通配符

事件名支持 `domain:*` 与 `*` 通配：

```js
events.on("world:*", (payload) => chat.log(`世界发生了点事`));
```

## 一次性与 Promise 风格

- `events.once(name, handler)`——只订阅一次触发，随后自动取消。
- `events.waitFor(name)`——返回 Promise，settled 时给出下一个负载。
- `events.waitForUntil(name, filter, timeoutMillis)`——等待匹配 `filter`
  的一次触发，超时后 reject；filter 运行在派发线程上，务必短小。

`waitFor` 可以和 `async`/`await` 组合：

```js
const { events, clock, chat } = neo;

async function watchDamage() {
    while (true) {
        const ev = await events.waitFor("player:damage");
        chat.actionbar(`哎哟！-${ev.amount.toFixed(1)} HP`);
        await clock.ticks(20);
    }
}
watchDamage();
```

## 同步处理器：`events.onSync`

少数事件是**可取消的**（`player:attackBlock`、`player:attackEntity`、
`player:interactBlock`、`player:interactEntity`）。只有它们接受同步订
阅，用 `events.onSync` 注册。回调在派发线程上运行，写法是过滤式的，类
似 `Array.filter`：返回 truthy 取消游戏的这个动作，falsy 放行。

```js
const { events } = neo;

// 取消在受保护高度以下攻击方块的所有尝试。
events.onSync("player:attackBlock", (payload) => {
    return payload.y < -60;   // truthy → 取消
});
```

同步回调必须短小：运行期间会阻塞派发线程。不要 `await`、不要长循环——
判断完立刻返回。

## 用 `clock` 计时

`clock` 的每个函数都返回 Promise，并绑定到调用脚本的事件循环：

| 调用 | 何时 resolve |
|---|---|
| `clock.nextTick()` | 当前客户端刻结束时 |
| `clock.ticks(count)` | 经过 `count` 个客户端刻后 |
| `clock.sleep(millis)` | 真实时间延迟后（与游戏刻无关） |
| `clock.runOnMain(task)` | 在游戏主线程执行完 `task` 后，并给出其返回值 |

当异步处理器需要触碰仅限主线程的状态时，`clock.runOnMain` 就是那座桥。

## 取消与清理

长时间运行的异步循环（比如上面的 `watchDamage`）会在脚本存续期间一直
运行——`/neomacros reload` 会随模块一起拆除它。想更早停下的，用标志位
或基于 `clock.sleep` 的截止时间，不要依赖未处理的 Promise 取消。

## 下一步

- 涉及文件或网络的脚本需要[权限与信任](./permissions-and-trust)。
- 完整事件目录见 [API 参考](/zh/api/)。
