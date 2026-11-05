# ItemStack

Data

不可变的物品堆副本。实例只来自查询（容器槽位、事件载荷）或 ；不提供直接构造器。JSON.stringify 输出纯字段。

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `count` | `number` | 物品数量。 (read-only) |
| `damage` | `number` | 当前耐久损伤（无耐久物品为 0）。 (read-only) |
| `enchantments` | `Array<Record<string, unknown>>` | 按 {id, level} 形态的附魔列表，按存储顺序。 (read-only) |
| `id` | `string` | 命名空间物品标识，如 minecraft:diamond_sword。 (read-only) |
| `isEmpty` | `boolean` | 物品堆是否为空（空气或数量为零）。 |
| `lore` | `Array<string>` | 按序排列的 Lore 行。 (read-only) |
| `maxDamage` | `number` | 最大耐久（无耐久物品为 0）。 (read-only) |
| `name` | `string` | 显示名（样式文本平铺）。 (read-only) |

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

物品标识是否等于给定 id（命名空间可省略）。

**Parameters**

- `itemId` `string`: 

**Returns** `boolean`

Since: 0.2.0 · MC: * · Loader: *

### `isSameItem(other: unknown)`

另一物品堆是否为同类物品（忽略数量、耐久与组件）。

**Parameters**

- `other` `unknown`: 

**Returns** `boolean`

Since: 0.2.0 · MC: * · Loader: *

### `of(data: ItemStackData)`

由中立端口快照构建脚本物品堆。

**Parameters**

- `data` `ItemStackData`: 

**Returns** [ItemStack](itemstack)

Since: 0.2.0

### `same(other: unknown)`

另一物品堆是否与本堆完全相同。

**Parameters**

- `other` `unknown`: 

**Returns** `boolean`

Since: 0.2.0 · MC: * · Loader: *

### `toJSON()`

JSON 形式，使 JSON.stringify 直接输出物品堆。

**Returns** `Record<string, unknown>`

Since: 0.2.0 · MC: * · Loader: *

### `toString()`



**Returns** `string`

Since: 0.2.0

