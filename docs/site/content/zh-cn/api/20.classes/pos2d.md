# Pos2D

Data

不可变二维点：旋转（yaw, pitch）与屏幕坐标。所有运算都返回新实例。

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `x` | `number` | X 坐标。 |
| `y` | `number` | Y 坐标。 |

## Methods

### `add(other: Pos2D)`

加上另一点。

**Parameters**

- `other` [Pos2D](pos2d): 

**Returns** [Pos2D](pos2d)

Since: 0.2.0 · MC: * · Loader: *

### `construct(args: unknown)`



**Parameters**

- `args` `unknown`: 

**Returns** `unknown`

Since: 0.2.0

### `equals(other: unknown)`



**Parameters**

- `other` `unknown`: 

**Returns** `boolean`

Since: 0.2.0

### `hashCode()`



**Returns** `number`

Since: 0.2.0

### `mul(other: Pos2D)`

与另一点按分量相乘。

**Parameters**

- `other` [Pos2D](pos2d): 

**Returns** [Pos2D](pos2d)

Since: 0.2.0 · MC: * · Loader: *

### `scale(factor: number)`

按系数缩放。

**Parameters**

- `factor` `number`: 

**Returns** [Pos2D](pos2d)

Since: 0.2.0 · MC: * · Loader: *

### `sub(other: Pos2D)`

减去另一点。

**Parameters**

- `other` [Pos2D](pos2d): 

**Returns** [Pos2D](pos2d)

Since: 0.2.0 · MC: * · Loader: *

### `toJSON()`

JSON 形式，使 JSON.stringify 直接输出坐标。

**Returns** `Record<string, unknown>`

Since: 0.2.0 · MC: * · Loader: *

### `toString()`



**Returns** `string`

Since: 0.2.0

