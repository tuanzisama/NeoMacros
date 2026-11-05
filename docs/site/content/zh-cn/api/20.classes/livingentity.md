# LivingEntity

Handle · Wraps: `net.minecraft.world.entity.LivingEntity` · Extends: [`Entity`](entity) · Extended by: [`Player`](player)

生物句柄：在实体基类之上增加生命与装备读面。

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `health` | `number` | 生命值；实体消失后为 undefined。 (read-only) |
| `heldItem` | [ItemStack](itemstack) | 主手物品；空手或实体消失时为 undefined。 (read-only) |
| `maxHealth` | `number` | 最大生命值；实体消失后为 undefined。 (read-only) |
| `offhandItem` | [ItemStack](itemstack) | 副手物品；空手或实体消失时为 undefined。 (read-only) |
| `sleeping` | `boolean` | 实体是否睡在床上。 |

## Methods

### `isHolding(itemId: string)`

主/副手任一是否持有指定 id 的物品（命名空间可省略）。

**Parameters**

- `itemId` `string`: 

**Returns** `boolean`

Since: 0.2.0 · MC: * · Loader: *

