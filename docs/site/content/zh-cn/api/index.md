# API 参考

脚本可见的门面都挂在全局 `neo` 对象下。TypeScript 定义以生成的
`neomacros.d.ts` 发布（[下载](../../neomacros.d.ts)，npm 包
`@neomacros/types`）；下面的模块页由 `docs-builder` 文档管线生成，每页
末尾附有该模块的简短示例。

刚开始写脚本？先看[指南](/zh-cn/guide/getting-started)，或到
[示例](/zh-cn/examples/)分区直接取一个完整脚本。

## 模块

### 核心

| 门面 | 用途 |
|---|---|
| [`events`](/zh-cn/api/events) | on / once / waitFor，onSync 可取消订阅 |
| [`thread`](/zh-cn/api/thread) | sleep、ticks、nextTick、runOnMain（根级 `clock.*` 同义） |
| [`storage`](/zh-cn/api/storage) | 按脚本隔离的键值持久化 |

### 界面

| 门面 | 用途 |
|---|---|
| [`chat`](/zh-cn/api/chat) | say、log、actionbar |
| [`hud`](/zh-cn/api/hud) | 持久 HUD 文本叠加层 |
| [`command`](/zh-cn/api/command) | 注册客户端命令 |
| [`render`](/zh-cn/api/render) | 世界坐标线框与线段 |

### 世界

| 门面 | 用途 |
|---|---|
| [`world`](/zh-cn/api/world) | 时间、维度、天气、方块查询 |
| [`player`](/zh-cn/api/player) | 本地玩家快照与状态效果 |
| [`entity`](/zh-cn/api/entity) | 按 UUID / 半径查询实体 |
| [`inventory`](/zh-cn/api/player) | 容器访问与物品移动 |

### 系统

| 门面 | 用途 |
|---|---|
| [`fs`](/zh-cn/api/fs) | 沙箱化文件访问（需能力授权） |
| [`net`](/zh-cn/api/net) | HTTP 请求（需能力授权） |
| [`java`](/zh-cn/api/java) | 原始 Java 互操作（需权限） |

### 输入

| 门面 | 用途 |
|---|---|
| [`input`](/zh-cn/api/input) | 自定义按键与滚轮事件 |

## 类与事件

每个包装类型（`Vec3`、`BlockPos`、`Block`、`BlockState`、`RayHit`、
`Player`、`LocalPlayer` 等）与每个可订阅事件（`player:damage`、
`world:join` 等）都有独立页面——在侧栏的 **类** 与 **事件** 分区浏览。

可取消事件（`player:attackBlock`、`player:attackEntity`、
`player:interactBlock`、`player:interactEntity`）必须使用
`events.onSync`；处理器返回 truthy 即取消游戏的该动作。
