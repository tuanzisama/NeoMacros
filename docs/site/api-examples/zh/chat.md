发送到服务器，或只在本地渲染：

```js
const { chat } = neo;

chat.say("所有人可见");        // 以玩家身份发送
chat.log("只有自己能看到");    // 本地聊天行
chat.actionbar("快捷栏上方");  // 临时叠加行
```
