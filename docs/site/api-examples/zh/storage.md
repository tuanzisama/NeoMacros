脚本私有的 JSON 持久化，跨重载与重启保留：

```js
const { storage } = neo;

storage.put("visits", { count: 1, last: "2026-11-01" });
const visits = storage.get("visits");

storage.remove("visits"); // 或 storage.put("visits", null)
```
