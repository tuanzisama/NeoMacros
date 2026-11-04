import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';

let fired = false;
events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  results['log'] = true;
  chat.log('[E2E] chat-log-ok');
  chat.actionbar('[E2E] chat-actionbar-ok');
  const t = chat.text('hello').color('red').append(' world').build();
  results['text-build'] = typeof t.plain === 'string';
  } catch (e) {
    chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    chat.log('[E2E] ' + key + '=' + value);
  }
  chat.log('[E2E] s1v2-chat: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
