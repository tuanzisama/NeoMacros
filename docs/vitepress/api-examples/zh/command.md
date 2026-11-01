注册一个客户端命令，再以编程方式触发它：

```js
const { command, chat } = neo;

command.register({
    name: "hello",
    description: "脚本打的一声招呼",
    handler: (args) => chat.log(`你好！参数：${args ?? "（无）"}`),
});

await command.execute("hello world"); // 以 "world" 为参数运行 /hello
```
