# ItemStack

Data

Immutable copy of one item stack. Instances come only from queries (inventory slots, event payloads) or the ; there is no direct constructor. JSON.stringify renders the plain fields.

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `count` | `number` | The stack size. (read-only) |
| `damage` | `number` | The current durability damage (0 for undamageable items). (read-only) |
| `enchantments` | `Array<Record<string, unknown>>` | The enchantments as {id, level} entries, in stored order. (read-only) |
| `id` | `string` | The namespaced item identifier, e.g. minecraft:diamond_sword. (read-only) |
| `isEmpty` | `boolean` | Whether the stack represents nothing (air or a zero count). |
| `lore` | `Array<string>` | The lore lines, in order. (read-only) |
| `maxDamage` | `number` | The maximum durability (0 for undamageable items). (read-only) |
| `name` | `string` | The display name (styled text flattened). (read-only) |

## Methods

### `equals(other: unknown)`



**Parameters**

- `other` `unknown`: 

**Returns** `boolean`

Since: 0.2.0

### `hashCode()`



**Returns** `number`

Since: 0.2.0

### `is(itemId: string)`

Whether the stack's item id equals the identifier (namespace optional).

**Parameters**

- `itemId` `string`: 

**Returns** `boolean`

Since: 0.2.0 · MC: * · Loader: *

### `isSameItem(other: unknown)`

Whether the other stack holds the same item, ignoring count, damage, and components.

**Parameters**

- `other` `unknown`: 

**Returns** `boolean`

Since: 0.2.0 · MC: * · Loader: *

### `of(data: ItemStackData)`

Builds the script stack from one neutral port snapshot.

**Parameters**

- `data` `ItemStackData`: 

**Returns** [ItemStack](itemstack)

Since: 0.2.0

### `same(other: unknown)`

Whether the other stack is a full copy of this one.

**Parameters**

- `other` `unknown`: 

**Returns** `boolean`

Since: 0.2.0 · MC: * · Loader: *

### `toJSON()`

JSON form so JSON.stringify renders the stack directly.

**Returns** `Record<string, unknown>`

Since: 0.2.0 · MC: * · Loader: *

### `toString()`



**Returns** `string`

Since: 0.2.0

