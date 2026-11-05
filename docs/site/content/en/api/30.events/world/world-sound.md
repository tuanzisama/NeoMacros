# world:sound

Payload of the sound event; fires when the client plays a sound. High-traffic, so wildcard dispatch skips it by default.

| Field | Type |
| --- | --- |
| `pitch` | `number` |
| `pos` | `Pos3D` |
| `soundId` | `string` |
| `volume` | `number` |

