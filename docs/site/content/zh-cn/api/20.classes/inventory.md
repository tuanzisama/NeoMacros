# Inventory

Handle · Extended by: [`PlayerInventory`](playerinventory)

物品栏句柄：槽位读取每次都重新查询物品栏端口；操作在主线程派发，处理完成后落定。

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `items` | `Array<Record<string, unknown>>` | 全部非空物品及其槽位号。 |
| `kind` | `string` | 物品栏种类（player、chest、crafting 等）。 (read-only) |
| `size` | `number` | 物品栏槽位总数。 (read-only) |
| `title` | `string` | 物品栏显示标题。 (read-only) |

## Methods

### `click(slot: number, button: number, shift: boolean)`

点击槽位（button 0 = 左键，1 = 右键；shift = 快速移动）。

**Parameters**

- `slot` `number`: 
- `button` `number`: 
- `shift` `boolean`: 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `close()`

关闭物品栏界面。

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `contains(itemId: string)`

物品栏中是否存在指定 id 的物品（命名空间可省略）。

**Parameters**

- `itemId` `string`: 

**Returns** `boolean`

Since: 0.2.0 · MC: * · Loader: *

### `countOf(item: string)`

统计某物品 id 的数量。

**Parameters**

- `item` `string`: 

**Returns** `number`

Since: 0.1.0 · MC: * · Loader: *

### `drop(slot: number, all: boolean)`

丢弃槽位中的物品（all = 全部）。

**Parameters**

- `slot` `number`: 
- `all` `boolean`: 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `findAll(itemId: string)`

持有指定物品 id 的槽位号列表（按序）。

**Parameters**

- `itemId` `string`: 

**Returns** `Array<number>`

Since: 0.2.0 · MC: * · Loader: *

### `findId(itemId: string)`

第一个持有指定物品 id 的槽位；无则 undefined。

**Parameters**

- `itemId` `string`: 

**Returns** `number`

Since: 0.1.0 · MC: * · Loader: *

### `quickMove(slot: number)`

将槽位物品快速移动到另一侧物品栏。

**Parameters**

- `slot` `number`: 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `slot(index: number)`

读取指定槽位的物品；越界或空槽为 undefined。

**Parameters**

- `index` `number`: 

**Returns** [ItemStack](itemstack)

Since: 0.1.0 · MC: * · Loader: *

### `swapHotbar(slot: number, hotbarIndex: number)`

将容器槽位与快捷栏槽位交换（0..8）。

**Parameters**

- `slot` `number`: 
- `hotbarIndex` `number`: 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

