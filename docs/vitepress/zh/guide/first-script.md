# 第一个脚本

本页带你完整走一遍第一个脚本：输出聊天、响应事件、不退出游戏热重载。

## 创建文件

创建 `config/neomacros/scripts/hello.js`：

```js
export function onEvent(ev) { }

const { events, chat } = neo;

chat.log("Hello from NeoMacros!");

events.on("player:join", (payload) => {
    chat.log(`欢迎，${payload.name}！`);
});
```

脚本就是普通的 ES 模块。这里有几件事值得注意：

- `neo` 是全局命名空间，挂载了所有[门面](/zh/api/)：`chat`、`events`、
  `world`、`player` 等。用到哪个就解构哪个。
- `export function onEvent(ev) { }` 是可选的——NeoMacros 把它识别为模块
  入口。留空（或不写）都没问题，真正的程序就是上面的顶层代码。
- 还有一个可选的 `meta` 导出，用来命名脚本、声明生命周期与权限
  （见[权限与信任](./permissions-and-trust)）：

  ```js
  export const meta = { name: "hello", lifecycle: "world" };
  ```

## 加载

默认 `world` 生命周期的脚本在世界会话开启时加载（想让脚本在游戏启动时
就加载，可在 `meta` 里写 `lifecycle: "always"`）。不重启游戏也能生效，
运行：

```text
/neomacros reload
```

再用 `/neomacros list` 确认脚本已加载。聊天区应出现 `Hello from
NeoMacros!`——`chat.log` 只在本地渲染，而 `chat.say` 会把消息发送到服务
器，就像玩家自己输入的一样。

## 响应事件

`events.on("player:join", ...)` 订阅了进服事件。处理器运行在脚本自己的
事件循环上，永远不会卡住游戏。每次订阅都会返回句柄，调用句柄上的
`.deactivate()`（或 `events.off(handle)`）即可取消订阅。

试着进入一个世界——每次进服都会打印一条欢迎。

## 出错时

脚本抛错时 NeoMacros 会捕获它，其他脚本不受影响。打开 `/neomacros
errors` 可查看最近的失败记录，含指回你文件的堆栈。

## 下一步

- 深入了解[事件与异步模式](./events-and-async)。
- 看看脚本需要[额外权限](./permissions-and-trust)时会发生什么。
- [示例](/zh/examples/)分区有完整的可运行演示。
