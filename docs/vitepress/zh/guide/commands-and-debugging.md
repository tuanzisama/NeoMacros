# 命令与排错

脚本的管理与排错都围绕 `/neomacros` 客户端命令及其打开的界面进行。

## 管理命令

| 命令 | 用途 |
|---|---|
| `/neomacros list` | 显示所有已加载脚本、生命周期与状态 |
| `/neomacros reload` | 重新扫描 `config/neomacros/scripts` 并应用变更 |
| `/neomacros trust <script>` | 为脚本授予其声明的能力（见[权限与信任](./permissions-and-trust)） |
| `/neomacros errors` | 打开最近脚本错误界面 |

日常编辑循环是：保存文件 → `/neomacros reload` → 观察效果。重载会卸载
旧模块（它的订阅、HUD 与渲染层随之消失），再加载新内容。

## 脚本内日志

开发期用 [`chat`](/zh/api/chat) 输出：

```js
const { chat } = neo;

chat.log("只渲染在本地聊天区");       // 本地聊天行
chat.actionbar("快捷栏上方，会淡出");  // 动作栏
```

`chat.log` 只在本地渲染——不会发到服务器——所以可以放心刷调试日志。

## 读懂错误界面

处理器抛出的异常会被 NeoMacros 捕获，不会把游戏带崩。`/neomacros
errors` 列出最近的失败，含脚本名、消息与指回你文件的堆栈。常见条目：

- **`... is not a function`**——门面调用拼写错误，或在模块加载完成前使用
  了成员。对照 [API 参考](/zh/api/) 检查拼写。
- **Permission denied**——脚本调用了受保护成员（`fs`、`net`、`command`）
  但未获信任。通过提示或 `/neomacros trust` 批准。
- **句柄读到 undefined**——`player.self`、`entity.lookup(...)` 等在不在世
  界时（或实体消失后）读回 `undefined`。用 `valid` 检查守卫，不要假设一
  定存在。

## 常见坑

- **可取消事件必须用 `onSync`。** `events.on` 的处理器无法取消
  `player:attackBlock` 等事件；只有 `events.onSync` 接受它们，且回调必须
  短小，因为它阻塞派发线程。
- **storage 的值只能是 JSON 数据。** [`storage.put`](/zh/api/storage) 接受
  普通对象、数组、字符串、数字、布尔——不接受类实例、Map 或函数。传
  `null` 即删除该键。
- **`fs` 路径相对于游戏目录。** 绝对路径与越出游戏目录的相对跳转都会抛
  错；沙箱无法绕过。
- **异步处理器是异步的。** 忘了 `await` 的处理器不会等待其工作完成——
  fire-and-forget Promise 里的错误会出现在错误界面，而不是调用点。

## 下一步

- 命令与事件完整列表：[API 参考](/zh/api/)。
- 真实条件下的完整演示：[示例](/zh/examples/)。
