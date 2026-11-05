# ChunkPos

Data

Immutable chunk-grid coordinate. Deliberately unrelated to : chunk coordinates and block coordinates are different spaces and every conversion between them is explicit (x = blockX >> 4).

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `minX` | `number` | The west (-X) block-coordinate boundary, equal to x << 4. |
| `minZ` | `number` | The north (-Z) block-coordinate boundary, equal to z << 4. |
| `x` | `number` | The chunk X (blockX >> 4). |
| `z` | `number` | The chunk Z. |

## Methods

### `construct(args: unknown)`



**Parameters**

- `args` `unknown`: 

**Returns** `unknown`

Since: 0.2.0

### `contains(pos: Pos3D)`

Whether the block position falls inside this chunk.

**Parameters**

- `pos` [Pos3D](pos3d): 

**Returns** `boolean`

Since: 0.2.0 · MC: * · Loader: *

### `equals(other: unknown)`



**Parameters**

- `other` `unknown`: 

**Returns** `boolean`

Since: 0.2.0

### `hashCode()`



**Returns** `number`

Since: 0.2.0

### `minBlock()`

The northwest corner block position on the world floor.

**Returns** [BlockPos](blockpos)

Since: 0.2.0 · MC: * · Loader: *

### `offset(dx: number, dz: number)`

Translates by chunk-space offsets.

**Parameters**

- `dx` `number`: 
- `dz` `number`: 

**Returns** [ChunkPos](chunkpos)

Since: 0.2.0 · MC: * · Loader: *

### `toJSON()`

JSON form so JSON.stringify renders the chunk directly.

**Returns** `Record<string, unknown>`

Since: 0.2.0 · MC: * · Loader: *

### `toString()`



**Returns** `string`

Since: 0.2.0

