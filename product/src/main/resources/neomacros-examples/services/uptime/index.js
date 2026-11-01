// Service script: runs for the whole session. Prints the uptime every
// 6000 ticks (5 minutes).
// Stop it with /neomacros service stop uptime, or from the panel.
import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';

let ticks = 0;

events.on('tick', () => {
    ticks++;
    if (ticks % 6000 === 0) {
        const minutes = Math.floor(ticks / 1200);
        chat.log('[uptime] this session has run for ' + minutes + ' minutes');
    }
});
