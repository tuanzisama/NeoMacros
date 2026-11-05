# key:press

Cancellable

Payload of the raw keyboard event; synchronous subscribers may cancel the key press before the game processes it.

| Field | Type |
| --- | --- |
| `action` | `string` |
| `code` | `number` |
| `key` | `string` |
| `modifiers` | `Array<string>` |

