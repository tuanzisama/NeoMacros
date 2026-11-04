// Event script: NeoMacros auto-subscribes to the events declared in
// config.event.on (see neomacro.yml) and calls startup(ev) per occurrence
// on this script's event loop. Subscriptions are released automatically
// when the script unloads (reload/quit) - no manual cleanup.
import { chat } from '@neomacros/chat';

export function startup(ev) {
    chat.log('[hello-world] welcome back! your NeoMacros event script is running.');
}
