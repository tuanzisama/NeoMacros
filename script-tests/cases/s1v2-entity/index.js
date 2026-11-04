import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';
import { entity } from '@neomacros/entity';
import { Pos3D } from '@neomacros/types';

let fired = false;
events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  const all = entity.players();
  results['players'] = Array.isArray(all);
  const near = entity.inRadius(new Pos3D(0, 64, 0), 64);
  results['radius'] = Array.isArray(near);
  } catch (e) {
    chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    chat.log('[E2E] ' + key + '=' + value);
  }
  chat.log('[E2E] s1v2-entity: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
