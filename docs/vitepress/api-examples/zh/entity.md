查询某点周围的实体，或按 UUID 定位一个：

```js
const { entity, chat } = neo;

const near = entity.inRadius(0, 64, 0, 16);
chat.log(`0/64/0 周围有 ${near.length} 个实体`);

const found = entity.lookup("d7f8a1c0-0000-0000-0000-000000000000");
if (found?.valid) {
    chat.log(`${found.type} 位于 ${found.x}, ${found.y}, ${found.z}`);
}
```
