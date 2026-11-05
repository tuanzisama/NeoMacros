# LocalPlayer

Handle · Wraps: `net.minecraft.client.player.LocalPlayer` · Extends: [`Player`](player)

本地玩家动作面：attack/interact/swing/drop 经游戏主线程派发，处理完成后落定。

## Methods

### `attack(target: Entity)`

攻击目标句柄背后的实体。

**Parameters**

- `target` [Entity](entity): 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `dropHeld(all: boolean)`

丢弃手持物品（all = 整组）。

**Parameters**

- `all` `boolean`: 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `interactBlock(pos: BlockPos)`

与坐标句柄背后的方块交互（右键）。

**Parameters**

- `pos` [BlockPos](blockpos): 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `interactEntity(target: Entity)`

与目标句柄背后的实体交互（右键）。

**Parameters**

- `target` [Entity](entity): 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `swingHand(offhand: boolean)`

挥动手（offhand = 副手）。

**Parameters**

- `offhand` `boolean`: 

**Returns** `Promise<void>`

Since: 0.1.0 · MC: * · Loader: *

### `toString()`

人类可读形式。

**Returns** `string`

