Wait in ticks or real time, and hop to the main thread when needed:

```js
const { clock } = neo;

await clock.ticks(20);   // one second of game ticks
await clock.sleep(500);  // half a second of real time
await clock.nextTick();  // end of the current tick

const result = await clock.runOnMain(() => "computed on the main thread");
```
