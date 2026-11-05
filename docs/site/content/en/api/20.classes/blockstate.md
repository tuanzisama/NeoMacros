# BlockState

Data

Immutable block state: the namespaced block identifier plus the vanilla state properties (facing, open, ...).

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `id` | `string` | The namespaced block identifier, e.g. minecraft:chest. (read-only) |
| `isAir` | `boolean` | Whether the state is one of the air blocks. |
| `liquid` | `boolean` | Whether the state is a liquid (water or lava family). |
| `props` | `Record<string, string>` | The vanilla state properties. (read-only) |

## Methods

### `is(idOrPredicate: string)`

Whether the state's block matches the identifier (namespace optional).

**Parameters**

- `idOrPredicate` `string`: 

**Returns** `boolean`

Since: 0.1.0 · MC: * · Loader: *

### `isProps(props: Record<string, string>)`

Whether the state matches an exact property map (every entry equals; an id key also compares the identifier).

**Parameters**

- `props` `Record<string, string>`: 属性表

**Returns** `boolean`

Since: 0.2.0 · MC: * · Loader: *

### `toString()`



**Returns** `string`

