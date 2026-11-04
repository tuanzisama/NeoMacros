# world

Fixture module exercising the full annotation set with complete bilingual documentation: class and member annotations, a handle type, a referenced record type, and an overridden parameter type.

Category: test · Since: 0.1.0 · MC availability: >=1.20.6 · Exposed as `neo.world` and `@neomacros/world`

**Class tree**

- `WorldHandle`
  - `RegionHandle`
- `WorldInfo`

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `time` | `number` | Returns the world time of day in ticks. (read-only) |

## Methods

### `mark(pos: FixturePos)`

Marks a block highlight at the given position.

**Parameters**

- `pos` [FixturePos](types.html#fixturepos): the block position to mark

**Returns** `Promise<void>` — a promise completing once the highlight is placed

Since: 0.1.0 · MC: >=1.20.6

### `watch(listener: (message: string) => void)`

Watches chat messages through the given listener.

**Parameters**

- `listener` `(message: string) => void`: callback invoked for each message

Since: 0.1.0 · Permission: `command` · Experimental

## Referenced Types

The following types appear in method signatures on this page.

### FixturePos

| Field | Type |
| --- | --- |
| `x` | `number` |
| `y` | `number` |
| `z` | `number` |

## Class RegionHandle

Handle · Wraps: `net.minecraft.world.fixture.Region` · Extends: `WorldHandle`

Live handle for one region, extending the world handle.

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `radius` | `number` | Returns the region radius in blocks. (read-only) |

## Class WorldHandle

Handle · Wraps: `net.minecraft.world.fixture.World` · Extended by: `RegionHandle`

Live handle for the fixture world.

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `name` | `string` | Returns the handle display name. (read-only) |

## Methods

### `refresh()`

Refreshes the handle against the live world.

Since: 0.1.0

## Class WorldInfo

Data · Wraps: `net.minecraft.world.fixture.World`

Immutable data description of a world.

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `id` | `string` | Returns the world identifier. (read-only) |

