import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';
import { input } from '@neomacros/input';

let fired = false;
events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  const sub = input.registerKeyPress('key.keyboard.f24', () => {});
  results['register'] = typeof sub.cancel === 'function';
  sub.cancel();
  } catch (e) {
    chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    chat.log('[E2E] ' + key + '=' + value);
  }
  chat.log('[E2E] s1v2-input: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
