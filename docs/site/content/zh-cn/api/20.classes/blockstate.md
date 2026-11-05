# BlockState

Data

不可变方块状态：带命名空间的方块标识符加原版状态属性（facing、open 等）。

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `id` | `string` | 带命名空间的方块标识符，如 minecraft:chest。 (read-only) |
| `isAir` | `boolean` | 状态是否为空气类方块。 |
| `liquid` | `boolean` | 状态是否为液体（水/熔岩族）。 |
| `props` | `Record<string, string>` | 原版状态属性。 (read-only) |

## Methods

### `is(idOrPredicate: string)`

状态的方块是否匹配标识符（可省略命名空间）。

**Parameters**

- `idOrPredicate` `string`: 

**Returns** `boolean`

Since: 0.1.0 · MC: * · Loader: *

### `isProps(props: Record<string, string>)`

状态是否与精确属性表匹配（每项都相等；id 键同时比较标识符）。

**Parameters**

- `props` `Record<string, string>`: 属性表

**Returns** `boolean`

Since: 0.2.0 · MC: * · Loader: *

### `toString()`



**Returns** `string`

