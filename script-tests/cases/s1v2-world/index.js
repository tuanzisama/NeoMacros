import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';
import { world } from '@neomacros/world';

let fired = false;
events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  results['getters'] = typeof world.dimension === 'string'
      && typeof world.time === 'number' && typeof world.raining === 'boolean'
      && typeof world.day === 'number' && typeof world.thundering === 'boolean'
      && typeof world.difficulty === 'string';
  } catch (e) {
    chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    chat.log('[E2E] ' + key + '=' + value);
  }
  chat.log('[E2E] s1v2-world: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
