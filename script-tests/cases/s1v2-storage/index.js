import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';
import { storage } from '@neomacros/storage';

let fired = false;
events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  storage.put('k', { n: 1 });
  const v = storage.get('k');
  results['roundtrip'] = v !== undefined && v.n === 1;
  storage.remove('k');
  } catch (e) {
    chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    chat.log('[E2E] ' + key + '=' + value);
  }
  chat.log('[E2E] s1v2-storage: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
