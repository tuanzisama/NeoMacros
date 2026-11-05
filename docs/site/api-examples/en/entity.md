Query the entities around a point, or pin one by UUID:

```js
const { entity, chat } = neo;

const near = entity.inRadius(0, 64, 0, 16);
chat.log(`${near.length} entities around 0/64/0`);

const found = entity.lookup("d7f8a1c0-0000-0000-0000-000000000000");
if (found?.valid) {
    chat.log(`${found.type} at ${found.x}, ${found.y}, ${found.z}`);
}
```
