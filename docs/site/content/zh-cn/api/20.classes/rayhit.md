# RayHit

Handle

活射线检测结果：沿射线的首个命中。block 与 entity 互斥，均为活句柄。

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `block` | [Block](block) | 命中方块句柄；命中实体或未命中时为 undefined。 (read-only) |
| `distance` | `number` | 原点到命中点的距离（未命中时为最大距离）。 (read-only) |
| `entity` | [Entity](entity) | 命中实体句柄；命中方块或未命中时为 undefined。 (read-only) |
| `hit` | `boolean` | 射线是否命中了任何目标。 |
| `pos` | [Pos3D](pos3d) | 精确命中位置。 (read-only) |

