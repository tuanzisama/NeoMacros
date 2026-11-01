# 第一个脚本

本页带你完整走一遍第一个脚本：一个带清单声明的脚本文件夹、输出聊天、
响应事件、不退出游戏热重载。

## 创建文件夹

每个脚本独占一个文件夹，放在三个分类目录之一下面。分类决定脚本的用途，
GUI 与命令也只从对应目录里选择候选：

| 目录 | 分类 | 运行时机 |
|---|---|---|
| `macros/keypress/<名字>/` | 按键脚本 | 绑定的按键被按下时 |
| `macros/event/<名字>/` | 事件脚本 | 其订阅的事件触发时 |
| `macros/services/<名字>/` | 服务脚本 | 游戏启动拉起、退出停止（面板/命令可手动启停） |

创建清单文件 `config/neomacros/macros/event/hello/neomacro.yml`：

```yaml
name: hello            # 唯一 ID：命令、信任表、启停表都以它为准
version: 1.0.0
author: you            # name / version / author 三项必填
description: 进世界时打招呼
```

清单支持 `neomacro.yml`、`neomacro.json`、`neomacro.json5` 三种格式（多个
并存时按此优先级取一）。可选字段包括 `description`、`license`、`homepage`、
`supportURL`、`main`（入口文件，默认 `index.js`）、`key`（按键脚本的默认
绑定）、`lifecycle`（`always` / `world` / `manual`；目录默认：services 为
`always`，其余为 `world`）以及 `permissions`（见
[权限与信任](./permissions-and-trust)）。

## 添加入口文件

创建 `config/neomacros/macros/event/hello/index.js`：

```js
const { events, chat } = neo;

chat.log("Hello from NeoMacros!");

events.on("world:join", (payload) => {
    chat.log("欢迎回来！");
});
```

脚本就是普通的 ES 模块。这里有几件事值得注意：

- `neo` 是全局命名空间，挂载了所有[门面](/zh/api/)：`chat`、`events`、
  `world`、`player` 等。用到哪个就解构哪个。
- 顶层代码就是程序本体，脚本加载时执行一次。可选的
  `export function dispose() { }` 做额外的清理——订阅本来就会自动注销。

## 加载

`world` 生命周期的脚本在世界会话开启时加载（`always` 随游戏启动——服务
目录的默认值）。不重启游戏也能生效，运行：

```text
/neomacros reload
```

再用 `/neomacros list` 确认脚本已加载。聊天区应出现 `Hello from
NeoMacros!`——`chat.log` 只在本地渲染，而 `chat.say` 会把消息发送到服务
器，就像玩家自己输入的一样。

## 绑定按键（按键脚本）

`macros/keypress/` 下的脚本在清单里写默认按键（`key: key.keyboard.j`），
并导出按键触发时调用的处理函数：

```js
export function onKey(ev) {
    neo.chat.log(`按下了 ${ev.key}`);
}
```

在管理面板（`/neomacros panel`）或用 `/neomacros keypress bind|unbind`
改绑定的记录保存在中央绑定表中，优先于清单默认值。

## 响应事件

`events.on("world:join", ...)` 订阅了进世界事件。处理器运行在脚本自己的
事件循环上，永远不会卡住游戏。每次订阅都会返回句柄，调用句柄上的
`.deactivate()`（或 `events.off(handle)`）即可取消订阅。

试着进入一个世界——每次进世界都会打印一条欢迎。

## 出错时

清单非法或入口缺失时，该文件夹会被跳过，原因显示在 `/neomacros errors`
和管理面板里。脚本抛错时 NeoMacros 会捕获它，其他脚本不受影响；打开
`/neomacros errors` 可查看最近的失败记录，含指回你文件的堆栈。

## 下一步

- 深入了解[事件与异步模式](./events-and-async)。
- 看看脚本需要[额外权限](./permissions-and-trust)时会发生什么。
- [示例](/zh/examples/)分区有完整的可运行演示。
