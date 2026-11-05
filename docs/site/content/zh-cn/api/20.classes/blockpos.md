# BlockPos

Data · Extends: [`Pos3D`](pos3d)

不可变的方块网格坐标：整数化的 ，带六个方向的偏移方法。new BlockPos(10.7, 64.2, 200.9) 会向下取整到网格。

## Methods

### `center()`

方块中心的世界坐标点。

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `construct(args: unknown)`



**Parameters**

- `args` `unknown`: 

**Returns** `unknown`

Since: 0.2.0

### `down()`

下移一格。

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `down(n: number)`

下移 n 格。

**Parameters**

- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `east()`

东移一格（X 加一）。

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `east(n: number)`

东移 n 格（X 加 n）。

**Parameters**

- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.2.0 · MC: * · Loader: *

### `north()`

北移一格（Z 减一）。

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `north(n: number)`

北移 n 格（Z 减 n）。

**Parameters**

- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.2.0 · MC: * · Loader: *

### `offset(direction: string, n: number)`

沿六个基本方向之一偏移 n 格。

**Parameters**

- `direction` `string`: 
- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `south()`

南移一格（Z 加一）。

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `south(n: number)`

南移 n 格（Z 加 n）。

**Parameters**

- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.2.0 · MC: * · Loader: *

### `toString()`



**Returns** `string`

Since: 0.2.0

### `up()`

上移一格。

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `up(n: number)`

上移 n 格。

**Parameters**

- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `west()`

西移一格（X 减一）。

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `west(n: number)`

西移 n 格（X 减 n）。

**Parameters**

- `n` `number`: 

**Returns** [BlockPos](blockpos)

Since: 0.2.0 · MC: * · Loader: *

