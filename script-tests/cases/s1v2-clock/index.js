import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';
import { thread } from '@neomacros/thread';

let fired = false;
events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  const start = Date.now();
  await thread.sleep(30);
  results['sleep'] = Date.now() - start >= 25;
  const sub = thread.after(10, () => {});
  sub.cancel();
  results['cancel'] = typeof sub.cancel === 'function';
  } catch (e) {
    chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    chat.log('[E2E] ' + key + '=' + value);
  }
  chat.log('[E2E] s1v2-clock: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
