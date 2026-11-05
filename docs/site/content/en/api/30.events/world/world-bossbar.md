# world:bossbar

Payload of the boss bar event; fires for each boss bar operation the client receives: add, remove, updateProgress, updateName, updateStyle, updateProperties. Fields the operation does not carry are null.

| Field | Type |
| --- | --- |
| `action` | `string` |
| `name` | `string` |
| `progress` | `number` |
| `uuid` | `string` |

