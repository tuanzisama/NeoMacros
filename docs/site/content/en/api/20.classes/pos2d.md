# Pos2D

Data

Immutable two-dimensional point: rotations (yaw, pitch) and screen coordinates. Every operation returns a new instance.

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `x` | `number` | The X coordinate. |
| `y` | `number` | The Y coordinate. |

## Methods

### `add(other: Pos2D)`

Adds another point.

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

Multiplies component-wise by another point.

**Parameters**

- `other` [Pos2D](pos2d): 

**Returns** [Pos2D](pos2d)

Since: 0.2.0 · MC: * · Loader: *

### `scale(factor: number)`

Scales by a factor.

**Parameters**

- `factor` `number`: 

**Returns** [Pos2D](pos2d)

Since: 0.2.0 · MC: * · Loader: *

### `sub(other: Pos2D)`

Subtracts another point.

**Parameters**

- `other` [Pos2D](pos2d): 

**Returns** [Pos2D](pos2d)

Since: 0.2.0 · MC: * · Loader: *

### `toJSON()`

JSON form so JSON.stringify renders the point directly.

**Returns** `Record<string, unknown>`

Since: 0.2.0 · MC: * · Loader: *

### `toString()`



**Returns** `string`

Since: 0.2.0

