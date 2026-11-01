Keybinds appear in the vanilla controls menu under NeoMacros:

```js
const { input } = neo;

const handle = input.registerKeybind("key.keyboard.k", () => {
    // fires on every press, repeats while held
});

input.unregister(handle); // when you no longer need it
```
