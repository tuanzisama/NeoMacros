世界坐标叠加层按命名层累积，直到清除：

```js
const { render } = neo;

render.drawLine("path",
    { x: 0, y: 64, z: 0 }, { x: 10, y: 64, z: 10 }, 0xFF00FF00);
render.drawBox("zone",
    { x: 0, y: 64, z: 0 }, { x: 5, y: 70, z: 5 }, 0xFF0000FF);

render.clearLayer("path"); // 只清掉这一层的图形
```
