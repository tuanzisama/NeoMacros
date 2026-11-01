向绝对 https 地址发起 HTTP 请求（需要 `net` 能力）：

```js
const { net } = neo;

const res = await net.get("https://api.example.com/status");
if (res.status === 200) {
    // res.body 是响应文本
}

await net.request("POST", "https://api.example.com/hook", '{"ok":true}', {
    "Content-Type": "application/json",
});
```
