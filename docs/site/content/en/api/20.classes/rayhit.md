# RayHit

Handle

Live raycast result: the first hit along the ray. block and entity are mutually exclusive; both are live handles.

## Properties

| Property | Type | Description |
| --- | --- | --- |
| `block` | [Block](block) | The hit block handle, or undefined on entity hits / misses. (read-only) |
| `distance` | `number` | The distance from the origin to the hit (max distance on a miss). (read-only) |
| `entity` | [Entity](entity) | The hit entity handle, or undefined on block hits / misses. (read-only) |
| `hit` | `boolean` | Whether the ray hit anything at all. |
| `pos` | [Pos3D](pos3d) | The exact hit position. (read-only) |

