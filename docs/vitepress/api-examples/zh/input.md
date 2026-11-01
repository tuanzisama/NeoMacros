按键绑定会出现在原版控制设置菜单的 NeoMacros 分类下：

```js
const { input } = neo;

const handle = input.registerKeybind("key.keyboard.k", () => {
    // 每次按下都触发，按住会重复
});

input.unregister(handle); // 不再需要时解除
```
