游戏目录下的文本文件（需要 `fs` 能力）：

```js
const { fs } = neo;

await fs.writeText("notes/hello.txt", "记得收土豆");
if (await fs.exists("notes/hello.txt")) {
    const text = await fs.readText("notes/hello.txt");
}
await fs.delete("notes/hello.txt");
```
