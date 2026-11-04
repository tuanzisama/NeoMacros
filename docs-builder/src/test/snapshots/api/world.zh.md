# world

练习完整注解集与完整双语文档的 fixture 模块：类级与方法级注解、句柄类型、签名引用的 record 类型与覆写的参数类型。

Category: test · Since: 0.1.0 · MC availability: >=1.20.6 · Exposed as `neo.world` and `@neomacros/world`

**Classes**

[`RegionHandle`](classes/RegionHandle.html) · [`WorldHandle`](classes/WorldHandle.html) · [`WorldInfo`](classes/WorldInfo.html)

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `time` | `number` | 返回当前世界时间（tick）。 (read-only) |

## Methods

### `mark(pos: FixturePos)`

在指定位置放置方块高亮。

**Parameters**

- `pos` [FixturePos](classes/FixturePos.html): the block position to mark

**Returns** `Promise<void>` — 高亮放置完成后兑现的 Promise

Since: 0.1.0 · MC: >=1.20.6

**Example**

```js
world.mark(new Pos3d(1, 64, 1));
```

### `watch(listener: (message: string) => void)`

通过监听器观察聊天消息。

**Parameters**

- `listener` `(message: string) => void`: callback invoked for each message

Since: 0.1.0 · Permission: `command` · Experimental

