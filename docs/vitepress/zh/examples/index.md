# 示例

完整可运行的脚本，演示真实的 NeoMacros 模式。每个示例都是单个 `.js`
文件：放进 `config/neomacros/scripts`，执行 `/neomacros reload` 即可运行。

| 演示 | 涉及内容 |
|---|---|
| [进服欢迎](./auto-greet) | `events.on`、聊天输出、事件负载字段 |
| [坐标 HUD](./coords-hud) | `tick` 事件、`hud` 叠加层、`player.self`、`render` 线段 |
| [方块守卫](./block-guard) | `input` 按键绑定、可取消的 `events.onSync` 处理器 |
| [伤害计数器](./damage-counter) | `storage` 跨重载与重启的持久化 |

## 怎么读这些示例

所有演示遵循同一结构：

1. 可选的 `meta` 导出（名称、生命周期、权限）；
2. 顶部解构用到的 `neo` 门面；
3. 实际干活的订阅或循环。

[指南](/zh/guide/getting-started)里的代码片段展示单次调用，这里的页面展
示完整脚本加逐段讲解。每个 [API 参考](/zh/api/)页面的底部也有对应模块
的短示例。

## 关于信任

这些演示都没有声明基础面之外的能力，加载时不会出现信任提示。如果你给
某个示例加上 `fs`、`net` 或 `command`，下次加载时会遇到
[权限提示](/zh/guide/permissions-and-trust)，属正常流程。
