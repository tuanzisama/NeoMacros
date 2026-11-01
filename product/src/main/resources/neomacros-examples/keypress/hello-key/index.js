// 按键脚本：当清单中的 key（或面板里改绑的按键）被按下时，
// NeoMacros 会在本脚本的事件循环里调用导出的 onKey(ev)。
// ev = { key: 'key.keyboard.j', clicks: 按键次数 }
//
// 想自己监听更多按键？也可以在代码里用 neo.input：
//   neo.input.registerKeyPress('key.keyboard.r', () => { ... });

export function onKey(ev) {
    neo.chat.log('[hello-key] 你按下了 ' + ev.key);
}
