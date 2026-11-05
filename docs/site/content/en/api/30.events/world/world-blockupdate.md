# world:blockUpdate

Payload of the block update event; fires for each block state change the client receives. High-traffic event: the payload carries only the fields needed to locate the change, and it is excluded from wildcard matching by default.

| Field | Type |
| --- | --- |
| `id` | `string` |
| `pos` | `BlockPos` |

