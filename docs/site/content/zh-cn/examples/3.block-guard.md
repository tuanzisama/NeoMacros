# 方块守卫

一个可开关的守卫：取消对受保护方块类型的攻击——顺带实战按键绑定与可
取消的同步处理器。

```js
// macros/event/block-guard/index.js — 可开关的基岩式保护
// manifest: macros/event/block-guard/neomacro.yml — name: block-guard, version: 1.0.0, author: you

const { events, input, chat } = neo;

let enabled = false;

input.registerKeybind("key.keyboard.k", () => {
    enabled = !enabled;
    chat.actionbar(enabled ? "方块守卫：开" : "方块守卫：关");
});

events.onSync("player:attackBlock", (payload) => {
    return enabled && payload.blockId === "minecraft:bedrock";
});
```

## 逐段讲解

**按键绑定。** `input.registerKeybind(keyName, sink)` 注册一个持久绑定，
会出现在原版控制设置菜单的 NeoMacros 分类下。按 <kbd>K</kbd> 切换开关，
回调在每次按下时触发。

**同步处理器。** `player:attackBlock` 是可取消事件，可取消事件只接受通
过 `events.onSync` 注册的同步订阅。回调在派发线程上运行，写法是过滤式
的：**返回 truthy 取消这次攻击，falsy 放行。**

**保持短小。** 处理器只做一次比较就返回。这就是契约——同步回调运行期
间会阻塞派发线程，所以不要 `await`、不要循环、不要重活。像 `enabled`
这样的状态检查正是这里该做的事。

**负载。** `payload.blockId` 是被攻击方块的注册 id，附带其位置。要守卫
多个方块，用一个 id 的 `Set` 做成员判断即可。

## 变体

- 守卫一片区域：`payload.y` 低于阈值时取消。
- 同时保护交互：用同样的方式订阅 `player:interactBlock`。
- 显示拦截了什么：在处理器里记日志——但只用简短的 `chat.actionbar`，
  并记住它运行在派发线程上。
