// 事件脚本：顶层代码在脚本加载时执行一次，这里订阅进入世界事件。
// 订阅会在脚本卸载（重载/退出）时自动注销，无需手动清理。
import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';

events.on('world:join', () => {
    chat.log('[hello-world] 欢迎回来！NeoMacros 事件脚本已在运行。');
});
