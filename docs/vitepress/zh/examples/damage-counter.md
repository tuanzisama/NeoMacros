# 伤害计数器

统计你受到的总伤害——并用 [`storage`](/zh/api/storage) 让它在脚本重载
与游戏重启之间保留。

```js
// macros/event/damage-counter/index.js — 持久化的伤害总量
// manifest: macros/event/damage-counter/neomacro.yml — name: damage-counter, version: 1.0.0, author: you

const { events, storage, chat } = neo;

let total = storage.get("totalDamage") ?? 0;
chat.log(`目前已受到伤害：${total}`);

events.on("player:damage", (payload) => {
    total += payload.amount;
    storage.put("totalDamage", total);
    chat.actionbar(`总计：${total.toFixed(1)}`);
});
```

## 逐段讲解

**读回持久状态。** `storage.get(key)` 返回存储的值，不存在时返回
`null`；`?? 0` 让全新安装从干净的零开始。值会以存入时的 JSON 类型原样
返回。

**每次受击都持久化。** `player:damage` 触发时携带 `amount` 与受击后的
`health`。处理器累加后用 `storage.put(key, value)` 写回。值必须是可
JSON 序列化的普通数据——数字、字符串、布尔、数组、普通对象——数字自然
没问题。

**数据存在哪。** 每个脚本拥有一个以源文件名为键的独立存储。存储跨
`/neomacros reload` 与游戏重启保留，且只属于本脚本——其他脚本读不到也
覆盖不了 `totalDamage`。

**重置。** `storage.remove("totalDamage")`（或
`storage.put("totalDamage", null)`）清空计数器；想做成热键重置，可以接
上 [`input`](/zh/api/input) 的按键绑定。

## 变体

- 同时统计本次会话：再加一个不碰存储的内存变量，在 `player:join` 时重
  置。
- 存一个小对象——`storage.put("stats", { damage: total, hits: n })`——
  之后逐步扩展。
- `payload.health` 低于阈值时用 `chat.log` 记一条警告。
