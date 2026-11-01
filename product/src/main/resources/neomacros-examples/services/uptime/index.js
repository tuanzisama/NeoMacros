// 服务脚本：常驻运行。每 6000 tick（5 分钟）播报一次本次会话时长。
// 停止方式：/neomacros service stop uptime，或在管理面板中停止。
import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';

let ticks = 0;

events.on('tick', () => {
    ticks++;
    if (ticks % 6000 === 0) {
        const minutes = Math.floor(ticks / 1200);
        chat.log('[uptime] 本次游戏已运行 ' + minutes + ' 分钟');
    }
});
