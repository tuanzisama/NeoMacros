# world

Fixture module exercising the full annotation set with complete bilingual documentation: class and member annotations, a handle type, a referenced record type, and an overridden parameter type.

Category: test · Since: 0.1.0 · MC availability: >=1.20.6 · Exposed as `neo.world` and `@neomacros/world`

**Classes**

[`RegionHandle`](classes/RegionHandle.html) · [`WorldHandle`](classes/WorldHandle.html) · [`WorldInfo`](classes/WorldInfo.html)

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `time` | `number` | Returns the world time of day in ticks. (read-only) |

## Methods

### `mark(pos: FixturePos)`

Marks a block highlight at the given position.

**Parameters**

- `pos` [FixturePos](classes/FixturePos.html): the block position to mark

**Returns** `Promise<void>` — a promise completing once the highlight is placed

Since: 0.1.0 · MC: >=1.20.6

### `watch(listener: (message: string) => void)`

Watches chat messages through the given listener.

**Parameters**

- `listener` `(message: string) => void`: callback invoked for each message

Since: 0.1.0 · Permission: `command` · Experimental

