# Pos3D

Data · Extended by: [`BlockPos`](blockpos)

不可变三维点/向量：所有运算都返回新实例。位置、方向与偏移的脚本端主力类型；JSON.stringify 直接输出 {"x":…,"y":…,"z":…}。

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `x` | `number` | X 坐标。 |
| `y` | `number` | Y 坐标。 |
| `z` | `number` | Z 坐标。 |

## Methods

### `add(other: Pos3D)`

加上另一个点/向量。

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** [Pos3D](pos3d)

Since: 0.2.0 · MC: * · Loader: *

### `construct(args: unknown)`



**Parameters**

- `args` `unknown`: 

**Returns** `unknown`

Since: 0.2.0

### `cross(other: Pos3D)`

叉积。

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `distanceSq(other: Pos3D)`

到另一点的距离平方。

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** `number`

Since: 0.2.0 · MC: * · Loader: *

### `distanceTo(other: Pos3D)`

到另一点的距离。

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** `number`

Since: 0.1.0 · MC: * · Loader: *

### `div(other: Pos3D)`

与另一向量按分量相除。

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** [Pos3D](pos3d)

Since: 0.2.0 · MC: * · Loader: *

### `dot(other: Pos3D)`

点积。

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** `number`

Since: 0.1.0 · MC: * · Loader: *

### `equals(other: unknown)`



**Parameters**

- `other` `unknown`: 

**Returns** `boolean`

Since: 0.2.0

### `floor()`

将坐标向下取整到方块网格。

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `hashCode()`



**Returns** `number`

Since: 0.2.0

### `length()`

向量长度。

**Returns** `number`

Since: 0.1.0 · MC: * · Loader: *

### `lengthSq()`

向量长度的平方。

**Returns** `number`

Since: 0.1.0 · MC: * · Loader: *

### `lerp(other: Pos3D, t: number)`

向另一点按 t（0..1）线性插值。

**Parameters**

- `other` [Pos3D](pos3d): 
- `t` `number`: 

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `mul(other: Pos3D)`

与另一向量按分量相乘。

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** [Pos3D](pos3d)

Since: 0.2.0 · MC: * · Loader: *

### `normalize()`

同方向的单位向量（零向量保持为零）。

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `scale(factor: number)`

按系数缩放。

**Parameters**

- `factor` `number`: 

**Returns** [Pos3D](pos3d)

Since: 0.2.0 · MC: * · Loader: *

### `sub(other: Pos3D)`

减去另一个点/向量。

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `toJSON()`

JSON 形式，使 JSON.stringify 直接输出坐标。

**Returns** `Record<string, unknown>`

Since: 0.1.0 · MC: * · Loader: *

### `toString()`



**Returns** `string`

Since: 0.2.0

### `withX(value: number)`

返回替换 X 坐标后的副本。

**Parameters**

- `value` `number`: 

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `withY(value: number)`

返回替换 Y 坐标后的副本。

**Parameters**

- `value` `number`: 

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `withZ(value: number)`

返回替换 Z 坐标后的副本。

**Parameters**

- `value` `number`: 

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

