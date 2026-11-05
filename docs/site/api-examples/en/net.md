HTTP requests to absolute https URLs (requires the `net` capability):

```js
const { net } = neo;

const res = await net.get("https://api.example.com/status");
if (res.status === 200) {
    // res.body holds the response text
}

await net.request("POST", "https://api.example.com/hook", '{"ok":true}', {
    "Content-Type": "application/json",
});
```
