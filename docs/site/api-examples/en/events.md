Async subscriptions, one-shot waits, and cancellable sync handlers:

```js
const { events, chat } = neo;

const sub = events.on("player:damage", (p) => chat.log(`-${p.amount}`));
events.once("player:spawn", () => chat.log("first spawn"));
const sound = await events.waitFor("world:sound");

// Cancellable events require onSync; a truthy return cancels the action.
events.onSync("player:attackBlock", (p) => p.y < -60);
```
