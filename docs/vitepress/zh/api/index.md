# API 参考

脚本可见的门面都挂在全局 `neo` 对象下。TypeScript 定义以生成的
`neomacros.d.ts` 发布（[下载](/neomacros.d.ts)，npm 包
`@neomacros/types`）；下面的模块页由 `docs-builder` 文档管线生成，每页
末尾附有该模块的简短示例。

刚开始写脚本？先看[指南](/zh/guide/getting-started)，或到
[示例](/zh/examples/)分区直接取一个完整脚本。

## 模块

### 核心

| 门面 | 用途 |
|---|---|
| [`events`](/zh/api/events) | on / once / waitFor，onSync 可取消订阅 |
| [`clock`](/zh/api/clock) | sleep、ticks、nextTick、runOnMain（根级 `clock.*` 同义） |
| [`storage`](/zh/api/storage) | 按脚本隔离的键值持久化 |

### 界面

| 门面 | 用途 |
|---|---|
| [`chat`](/zh/api/chat) | say、log、actionbar |
| [`hud`](/zh/api/hud) | 持久 HUD 文本叠加层 |
| [`command`](/zh/api/command) | 注册客户端命令 |
| [`render`](/zh/api/render) | 世界坐标线框与线段 |

### 世界

| 门面 | 用途 |
|---|---|
| [`world`](/zh/api/world) | 时间、维度、天气、方块查询 |
| [`player`](/zh/api/player) | 本地玩家快照与状态效果 |
| [`entity`](/zh/api/entity) | 按 UUID / 半径查询实体 |

### 系统

| 门面 | 用途 |
|---|---|
| [`fs`](/zh/api/fs) | 沙箱化文件访问（需能力授权） |
| [`net`](/zh/api/net) | HTTP 请求（需能力授权） |

### 输入

| 门面 | 用途 |
|---|---|
| [`input`](/zh/api/input) | 自定义按键与滚轮事件 |

## 事件目录

所有内置事件的负载都是普通对象。主要事件：

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

可取消事件（`player:attackBlock`、`player:attackEntity`、
`player:interactBlock`、`player:interactEntity`）必须使用
`events.onSync`；处理器返回 truthy 即取消游戏的该动作。
