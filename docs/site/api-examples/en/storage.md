Per-script JSON persistence that survives reloads and restarts:

```js
const { storage } = neo;

storage.put("visits", { count: 1, last: "2026-11-01" });
const visits = storage.get("visits");

storage.remove("visits"); // or storage.put("visits", null)
```
