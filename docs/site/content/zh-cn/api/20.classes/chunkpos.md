# ChunkPos

Data

不可变区块坐标。刻意不与 发生继承关系：区块坐标与方块坐标分属两套坐标系，相互换算必须显式（x = blockX >> 4）。

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `minX` | `number` | 区块西（-X）边界方块坐标，等于 x << 4。 |
| `minZ` | `number` | 区块北（-Z）边界方块坐标，等于 z << 4。 |
| `x` | `number` | 区块 X（blockX >> 4）。 |
| `z` | `number` | 区块 Z。 |

## Methods

### `construct(args: unknown)`



**Parameters**

- `args` `unknown`: 

**Returns** `unknown`

Since: 0.2.0

### `contains(pos: Pos3D)`

判断方块坐标是否落在本区块内。

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

区块西北角的方块坐标（y=0，需要其他高度时用 withY 替换）。

**Returns** [BlockPos](blockpos)

Since: 0.2.0 · MC: * · Loader: *

### `offset(dx: number, dz: number)`

按区块坐标系平移。

**Parameters**

- `dx` `number`: 
- `dz` `number`: 

**Returns** [ChunkPos](chunkpos)

Since: 0.2.0 · MC: * · Loader: *

### `toJSON()`

JSON 形式，使 JSON.stringify 直接输出区块坐标。

**Returns** `Record<string, unknown>`

Since: 0.2.0 · MC: * · Loader: *

### `toString()`



**Returns** `string`

Since: 0.2.0

