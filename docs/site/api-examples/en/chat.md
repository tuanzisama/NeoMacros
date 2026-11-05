Send to the server, or render locally only:

```js
const { chat } = neo;

chat.say("visible to everyone");     // sent as if typed by the player
chat.log("only you see this");       // local chat line
chat.actionbar("above the hotbar");  // temporary overlay line
```
