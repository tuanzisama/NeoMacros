# 权限与信任

NeoMacros 脚本运行在沙箱里。读取世界状态、输出聊天始终允许，但任何越
出基础面的操作——文件、网络、命令——都必须由脚本**声明**、并由你（玩
家）显式**授权**。

## 声明能力

脚本在 `neomacro.yml` 清单中声明所需能力：

```yaml
# macros/event/backup-notes/neomacro.yml
name: backup-notes
version: 1.0.0
author: you                    # name / version / author 三项必填
lifecycle: world               # always | world（默认）| manual
permissions: ["fs"]            # 基础面之外的能力
```

| 权限 | 解锁 |
|---|---|
| `fs` | [`neo.fs`](/zh/api/fs) 的全部成员（readText、writeText、list、mkdir、delete、exists） |
| `net` | [`neo.net`](/zh/api/net) 的全部成员（get、request） |
| `command` | [`neo.command`](/zh/api/command) 的 register 与 execute |

没有 `permissions` 列表（或列表为空）的脚本只获得基础面，不会触发任何
提示。未知的能力名会被信任门拒绝——写错了也是安全失败。

## 信任提示

首次加载声明了能力的脚本时，聊天区会出现提示，列出所请求的内容。在你
做出决定之前，脚本运行在**拒绝视图**下——受保护调用会直接失败。授权
方式：

- 点击提示行（会替你执行 trust 命令），或
- 手动输入 `/neomacros trust <script>`。

```text
/neomacros trust backup-notes
```

两种方式都会为这个内容哈希持久化授权。

## 信任绑定内容

授权以脚本文件的 **SHA-256 内容哈希**为键。这意味着：

- 已授信的脚本永远无法悄悄扩权——修改文件即改变哈希，下次加载会重新
  出现提示；
- 信任决定跨重启保留（存于 `config.json`）。

修改后重新批准就是日常工作流——提示本身就是审核环节。

## 导入不越出脚本文件夹

脚本可以用 ES module 导入拆分代码（`import { fmt } from './lib/util.js'`），
但被导入的文件必须位于脚本自己的文件夹内：`..` 逃逸、绝对路径、指向外部
的 `file:` URI 都会在加载时被拒绝。这条边界属于沙箱本身——它不是权限，
清单声明也无法放宽。`@neomacros/*` 模块由模组自身提供，不受影响。

## 无需信任的操作

- [`events`](/zh/api/events)、[`thread`](/zh/api/thread)、[`chat`](/zh/api/chat)
- 通过 [`world`](/zh/api/world)、[`player`](/zh/api/player)、
  [`entity`](/zh/api/entity) 读取世界与玩家状态
- [`hud`](/zh/api/hud) 与 [`render`](/zh/api/render) 的 HUD 与世界叠加层
- [`input`](/zh/api/input) 注册按键
- [`storage`](/zh/api/storage)——脚本自己的键值存储

只用到这些的脚本，省略 `permissions` 列表即可无提示加载。
