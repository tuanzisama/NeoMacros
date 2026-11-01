# 进服欢迎

玩家加入时在本地记录一行日志。这是最小但有用的开服事件脚本——也是你
以后写一切监听器的模板。

```js
// auto-greet.js — 在本地聊天区播报进服
export const meta = { name: "auto-greet", lifecycle: "world" };

const { events, chat } = neo;

const seen = new Set();

events.on("player:join", (payload) => {
    if (seen.has(payload.name)) return;
    seen.add(payload.name);
    chat.log(`o/ ${payload.name} 进来了`);
    chat.actionbar(`${payload.name} 上线`);
});
```

## 逐段讲解

**`meta` 导出。** 脚本给自己命名，使用默认的 `world` 生命周期——世界会
话开启时加载，关闭时卸载。没有声明任何权限，所以不会出现信任提示。

**订阅。** `events.on("player:join", handler)` 在脚本自己的事件循环上注
册异步监听。负载携带玩家快照字段——`payload.name` 是加入玩家的名字。

**去重。** `seen` 集合避免重复进服（切换维度、断线重连）刷屏。脚本重载
时集合重置——调试迭代期间这通常正合需要。

**输出通道。** `chat.log` 只在本地聊天渲染——不会发到服务器。
`chat.actionbar` 在快捷栏上方显示一条会淡出的文字。两者都不会被其他玩
家看到。

## 变体

- 退出也播报：用同样的方式订阅 `player:leave`。
- 只关心自己？把 `payload.name` 与 [`player.self.name`](/zh/api/player)
  比较。
- 只想欢迎一次？用 `events.once`——首次触发后自动取消订阅。
