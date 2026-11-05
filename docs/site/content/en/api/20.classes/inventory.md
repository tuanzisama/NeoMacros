# Inventory

Handle · Extended by: [`PlayerInventory`](playerinventory)

Live inventory handle: slot reads re-query the inventory port on every access; actions dispatch on the main thread and resolve once processed.

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `items` | `Array<Record<string, unknown>>` | All non-empty stacks with their slot indices. |
| `kind` | `string` | The inventory kind (player, chest, crafting, ...). (read-only) |
| `size` | `number` | The inventory slot count. (read-only) |
| `title` | `string` | The inventory display title. (read-only) |

## Methods

### `click(slot: number, button: number, shift: boolean)`

Clicks a slot (button 0 = left, 1 = right; shift = quick move).

**Parameters**

- `slot` `number`: 
- `button` `number`: 
- `shift` `boolean`: 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `close()`

Closes the inventory screen.

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `contains(itemId: string)`

Whether the inventory holds the given item id (namespace optional).

**Parameters**

- `itemId` `string`: 

**Returns** `boolean`

Since: 0.2.0 · MC: * · Loader: *

### `countOf(item: string)`

Counts occurrences of one item id.

**Parameters**

- `item` `string`: 

**Returns** `number`

Since: 0.1.0 · MC: * · Loader: *

### `drop(slot: number, all: boolean)`

Drops the stack from a slot.

**Parameters**

- `slot` `number`: 
- `all` `boolean`: 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `findAll(itemId: string)`

Slot indices holding the given item id, in order.

**Parameters**

- `itemId` `string`: 

**Returns** `Array<number>`

Since: 0.2.0 · MC: * · Loader: *

### `findId(itemId: string)`

First slot holding the given item id, or undefined.

**Parameters**

- `itemId` `string`: 

**Returns** `number`

Since: 0.1.0 · MC: * · Loader: *

### `quickMove(slot: number)`

Quick-moves a slot to the other inventory.

**Parameters**

- `slot` `number`: 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `slot(index: number)`

Reads the stack at a slot, or undefined when out of range or empty.

**Parameters**

- `index` `number`: 

**Returns** [ItemStack](itemstack)

Since: 0.1.0 · MC: * · Loader: *

### `swapHotbar(slot: number, hotbarIndex: number)`

Swaps a container slot with a hotbar slot (0..8).

**Parameters**

- `slot` `number`: 
- `hotbarIndex` `number`: 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

