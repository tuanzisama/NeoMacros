# API 参考

脚本门面挂在全局 `neo` 对象下。TypeScript 定义由文档管线生成
（npm 包 `@neomacros/types` 的 `neomacros.d.ts`）；各模块完整页面由
`docs-builder` 管线产出。

| 门面 | 用途 |
|---|---|
| `neo.events` | on / once / waitFor，onSync 可取消订阅 |
| `neo.clock` | sleep、ticks、nextTick、runOnMain（根级 `clock.*` 同义） |
| `neo.chat` | say、log、actionbar |
| `neo.storage` | 按脚本隔离的键值持久化 |
| `neo.world` | 时间、维度、天气、方块查询 |
| `neo.entity` | 按 UUID / 半径查询实体 |
| `neo.player` | 本地玩家快照与状态效果 |
| `neo.fs` | 沙箱文件访问（需授权） |
| `neo.net` | HTTP 请求（需授权） |
| `neo.input` | 自定义按键与滚轮事件 |
| `neo.command` | 注册客户端命令 |
| `neo.hud` | 持久 HUD 文本覆盖层 |
| `neo.render` | 世界内线框方块与线段 |

## 事件目录

内置事件的负载均为纯 JS 对象。常用事件：

| 事件 | 负载字段 |
|---|---|
| `tick` | `tick` |
| `player:join` / `player:leave` / `player:spawn` | 玩家快照字段 |
| `player:damage` / `player:heal` | `amount`、`health` |
| `player:effect` | `effectId`、`added`、`amplifier` |
| `player:attackBlock`（可取消） | `blockId`、位置 |
| `world:blockUpdate` | `x`、`y`、`z`、`blockId` |
| `world:chunkLoad` / `world:chunkUnload` | `chunkX`、`chunkZ` |
| `world:sound` | `soundId`、位置、`volume`、`pitch` |
| `world:bossbar` | `uuid`、`action`、`name`、`progress` |
| `entity:damage` / `entity:heal` | `entity`、`amount` |
| `entity:load` / `entity:unload` | 实体标识字段 |

可取消事件（`player:attackBlock` 等）须用 `events.onSync` 订阅；处理
函数返回真值即取消游戏动作。
