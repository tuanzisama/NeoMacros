Register a client command, then trigger it programmatically:

```js
const { command, chat } = neo;

command.register({
    name: "hello",
    description: "greet from a script",
    handler: (args) => chat.log(`hello! args: ${args ?? "(none)"}`),
});

await command.execute("hello world"); // runs /hello with "world" as args
```
