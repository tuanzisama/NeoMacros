// Event script: the top-level code runs once when the script loads; here it
// subscribes to the world-join event. Subscriptions are released
// automatically when the script unloads (reload/quit) - no manual cleanup.
import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';

events.on('world:join', () => {
    chat.log('[hello-world] welcome back! your NeoMacros event script is running.');
});
