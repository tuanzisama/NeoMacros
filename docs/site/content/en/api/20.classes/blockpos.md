# BlockPos

Data · Extends: [`Pos3D`](pos3d)

Immutable block-grid position: an integer with directional offsets. new BlockPos(10.7, 64.2, 200.9) floors onto the grid.

## Methods

### `center()`

The center of the block as a world-coordinate point.

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `construct(args: unknown)`



**Parameters**

- `args` `unknown`: 

**Returns** `unknown`

Since: 0.2.0

### `down()`

One block down.

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `down(n: number)`

n blocks down.

**Parameters**

- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `east()`

One block east (positive X).

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `east(n: number)`

n blocks east (positive X).

**Parameters**

- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.2.0 · MC: * · Loader: *

### `north()`

One block north (negative Z).

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `north(n: number)`

n blocks north (negative Z).

**Parameters**

- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.2.0 · MC: * · Loader: *

### `offset(direction: string, n: number)`

Offsets by n steps along one of the six cardinal directions.

**Parameters**

- `direction` `string`: 
- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `south()`

One block south (positive Z).

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `south(n: number)`

n blocks south (positive Z).

**Parameters**

- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.2.0 · MC: * · Loader: *

### `toString()`



**Returns** `string`

Since: 0.2.0

### `up()`

One block up.

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `up(n: number)`

n blocks up.

**Parameters**

- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `west()`

One block west (negative X).

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `west(n: number)`

n blocks west (negative X).

**Parameters**

- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.2.0 · MC: * · Loader: *

