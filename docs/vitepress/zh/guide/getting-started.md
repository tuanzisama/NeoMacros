# 快速上手

NeoMacros 在 Minecraft 客户端内运行 ECMAScript（JavaScript）脚本。脚本
是普通 `.js` ES 模块，放在 `config/neomacros/scripts` 目录。

## 安装

1. 为你的游戏版本安装 [Fabric Loader](https://fabricmc.net/use/)（支持 26.1+）。
2. 将 `neomacros-<mc>-fabric-<version>.jar` 与对应版本的
   [Fabric API](https://modrinth.com/mod/fabric-api) 放入 `mods` 目录。
3. 启动一次游戏；NeoMacros 会创建 `config/neomacros/scripts`。

## 第一个脚本

创建 `config/neomacros/scripts/hello.js`：

```js
export function onEvent(ev) { }

const { events, chat } = neo;

chat.log("Hello from NeoMacros!");

events.on("player:join", (payload) => {
    chat.log(`欢迎，${payload.name}！`);
});
```

默认生命周期的脚本在游戏启动时加载。用 `/neomacros list` 查看已加载脚
本，`/neomacros reload` 重新加载。

## 订阅事件

`events.on` 注册监听并返回订阅句柄，调用 `.deactivate()` 取消。用
`events.waitFor(name)` 以 Promise 等待一次事件：

```js
const { events, clock } = neo;

async function watchDamage() {
    while (true) {
        const ev = await events.waitFor("player:damage");
        chat.actionbar(`受到 ${ev.amount.toFixed(1)} 点伤害！`);
        await clock.ticks(20);
    }
}
watchDamage();
```

## 信任与权限

脚本首次声明安全默认集之外的能力时，聊天区会出现提示：点击该行或运行
`/neomacros trust <script>`，按内容哈希授信。

## 屏幕与世界绘制

```js
const { hud, render } = neo;

hud.addText("coords", "坐标显示", 8, 8, 0xFFFFFFFF);

render.drawLine("path",
    { x: 0, y: 64, z: 0 }, { x: 10, y: 64, z: 10 }, 0xFF00FF00);
```

## 下一步

- 浏览 [API 参考](/zh/api/) 查看全部门面与事件负载。
- 游戏内 `/neomacros errors` 界面列出最近的脚本错误。
