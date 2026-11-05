# Block

Handle

Live block handle: state reads re-query the world on every access; a block outside the loaded world reads back as undefined instead of raising.

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `biome` | `string` | The biome identifier at the block position. (read-only) |
| `chunk` | [ChunkPos](chunkpos) | The chunk coordinate containing the block. (read-only) |
| `pos` | [BlockPos](blockpos) | The block position. (read-only) |
| `raw` | `unknown` | Escape hatch: the raw host block state object. Requires the java permission. |
| `state` | [BlockState](blockstate) | The current block state; undefined outside the loaded world. (read-only) |
| `valid` | `boolean` | Whether the block is still loaded in the world. |

## Methods

### `relative(direction: string, n: number)`

The block n steps in one cardinal direction, or undefined outside the loaded world.

**Parameters**

- `direction` `string`: 方向名（up/down/north/south/east/west）
- `n` `number`: 步数（可省略，默认 1）

**Returns** [Block](block)

Since: 0.2.0 · MC: * · Loader: *

### `toString()`

Human-readable form.

**Returns** `string`

