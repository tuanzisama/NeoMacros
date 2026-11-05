# Pos3D

Data · Extended by: [`BlockPos`](blockpos)

Immutable three-dimensional point and direction: every operation returns a new instance. The script-facing workhorse for positions, directions, and offsets; JSON.stringify renders it as {"x":…,"y":…,"z":…}.

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `x` | `number` | The X coordinate. |
| `y` | `number` | The Y coordinate. |
| `z` | `number` | The Z coordinate. |

## Methods

### `add(other: Pos3D)`

Adds another point or vector.

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

The cross product.

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `distanceSq(other: Pos3D)`

The squared distance to another point.

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** `number`

Since: 0.2.0 · MC: * · Loader: *

### `distanceTo(other: Pos3D)`

The distance to another point.

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** `number`

Since: 0.1.0 · MC: * · Loader: *

### `div(other: Pos3D)`

Divides component-wise by another vector.

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** [Pos3D](pos3d)

Since: 0.2.0 · MC: * · Loader: *

### `dot(other: Pos3D)`

The dot product.

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

Floors the coordinates onto the block grid.

**Returns** [BlockPos](blockpos)

Since: 0.1.0 · MC: * · Loader: *

### `hashCode()`



**Returns** `number`

Since: 0.2.0

### `length()`

The vector length.

**Returns** `number`

Since: 0.1.0 · MC: * · Loader: *

### `lengthSq()`

The squared vector length.

**Returns** `number`

Since: 0.1.0 · MC: * · Loader: *

### `lerp(other: Pos3D, t: number)`

Linear interpolation toward another point by t (0..1).

**Parameters**

- `other` [Pos3D](pos3d): 
- `t` `number`: 

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `mul(other: Pos3D)`

Multiplies component-wise by another vector.

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** [Pos3D](pos3d)

Since: 0.2.0 · MC: * · Loader: *

### `normalize()`

The unit vector of the same direction (zero vector stays zero).

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `scale(factor: number)`

Scales by a factor.

**Parameters**

- `factor` `number`: 

**Returns** [Pos3D](pos3d)

Since: 0.2.0 · MC: * · Loader: *

### `sub(other: Pos3D)`

Subtracts another point or vector.

**Parameters**

- `other` [Pos3D](pos3d): 

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `toJSON()`

JSON form so JSON.stringify renders the point directly.

**Returns** `Record<string, unknown>`

Since: 0.1.0 · MC: * · Loader: *

### `toString()`



**Returns** `string`

Since: 0.2.0

### `withX(value: number)`

Returns a copy with the X coordinate replaced.

**Parameters**

- `value` `number`: 

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `withY(value: number)`

Returns a copy with the Y coordinate replaced.

**Parameters**

- `value` `number`: 

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

### `withZ(value: number)`

Returns a copy with the Z coordinate replaced.

**Parameters**

- `value` `number`: 

**Returns** [Pos3D](pos3d)

Since: 0.1.0 · MC: * · Loader: *

