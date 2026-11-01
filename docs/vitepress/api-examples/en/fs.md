Text files under the game directory (requires the `fs` capability):

```js
const { fs } = neo;

await fs.writeText("notes/hello.txt", "remember the potatoes");
if (await fs.exists("notes/hello.txt")) {
    const text = await fs.readText("notes/hello.txt");
}
await fs.delete("notes/hello.txt");
```
