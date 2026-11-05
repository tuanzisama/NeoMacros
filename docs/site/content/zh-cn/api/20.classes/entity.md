# Entity

Handle · Wraps: `net.minecraft.world.entity.Entity` · Extended by: [`ItemEntity`](itementity), [`LivingEntity`](livingentity)

实体句柄：属性实时回读世界状态；实体消失后属性回读为 undefined 而不报错。

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `alive` | `boolean` | 实体是否加载且存活（生物需生命值大于零）。 |
| `blockPos` | [BlockPos](blockpos) | 方块网格坐标（向下取整）；实体消失后为 undefined。 (read-only) |
| `boundingBox` | `Record<string, unknown>` | 轴对齐包围盒 {min, max}；实体消失后为 undefined。 (read-only) |
| `chunk` | [ChunkPos](chunkpos) | 实体所在区块坐标；实体消失后为 undefined。 (read-only) |
| `dimension` | `string` | 实体所在维度标识；实体消失后为 undefined。 (read-only) |
| `direction` | [Pos3D](pos3d) | 视线方向单位向量；实体消失后为 undefined。 (read-only) |
| `eyeHeight` | `number` | 眼睛高度；实体消失后为 undefined。 (read-only) |
| `eyePos` | [Pos3D](pos3d) | 眼睛位置；实体消失后为 undefined。 (read-only) |
| `facing` | `string` | 离散水平朝向（north/south/east/west）；实体消失后为 undefined。 (read-only) |
| `gliding` | `boolean` | 实体是否在鞘翅滑翔。 |
| `inLava` | `boolean` | 实体是否在熔岩中。 |
| `inWater` | `boolean` | 实体是否在水中。 |
| `name` | `string` | 显示名称；实体消失后为 undefined。 (read-only) |
| `onFire` | `boolean` | 实体是否着火。 |
| `onGround` | `boolean` | 实体是否站在地面上。 |
| `passengers` | `Array<`[Entity](entity)`>` | 骑在本实体上的乘客句柄数组；无乘客时为空数组。 (read-only) |
| `pitch` | `number` | 俯仰角（角度）；实体消失后为 undefined。 (read-only) |
| `pos` | [Pos3D](pos3d) | 世界坐标位置；实体消失后为 undefined。 (read-only) |
| `pose` | `string` | 实体姿态名（standing、crouching 等）；实体消失后为 undefined。 (read-only) |
| `raw` | `unknown` | 穿透口：原始宿主实体对象（需 java 权限）。 |
| `rotation` | [Pos2D](pos2d) | 朝向 Pos2D(yaw, pitch)（角度）；实体消失后为 undefined。 (read-only) |
| `sneaking` | `boolean` | 实体是否在潜行。 |
| `sprinting` | `boolean` | 实体是否在疾跑。 |
| `swimming` | `boolean` | 实体是否在游泳。 |
| `type` | `string` | 实体类型标识（如 minecraft:pig）；实体消失后为 undefined。 (read-only) |
| `uuid` | `string` | 实体 UUID 字符串。 (read-only) |
| `valid` | `boolean` | 实体是否仍在世界中加载。 |
| `vehicle` | [Entity](entity) | 本实体骑乘的载具句柄；无骑乘时为 undefined。 (read-only) |
| `velocity` | [Pos3D](pos3d) | 速度向量；实体消失后为 undefined。 (read-only) |
| `yaw` | `number` | 偏航角（角度）；实体消失后为 undefined。 (read-only) |

## Methods

### `distanceTo(target: unknown)`

到另一实体或坐标点的距离。

**Parameters**

- `target` `unknown`: 实体句柄或 Pos3D

**Returns** `number` — 距离（格）

Since: 0.1.0 · MC: * · Loader: *

### `snapshot()`

返回时点快照；实体消失时返回 null。

**Returns** `EntitySnapshot`

Since: 0.1.0 · MC: * · Loader: *

### `toJSON()`

等价 snapshot 的 JSON 形式；JSON.stringify 可直接序列化句柄。

**Returns** `Record<string, unknown>`

Since: 0.1.0 · MC: * · Loader: *

### `toString()`

人类可读形式。

**Returns** `string`

