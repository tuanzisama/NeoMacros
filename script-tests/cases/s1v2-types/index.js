import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';
import { Pos3D, BlockPos } from '@neomacros/types';

let fired = false;
events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  const v = new Pos3D(1, 2, 3);
  const w = new Pos3D(4, 5, 6);
  const sum = v.add(w);
  const bp = new BlockPos(10, 64, 200);
  results['vec3'] = sum.x === 5 && sum.y === 7 && sum.z === 9;
  results['lengthSq'] = v.lengthSq() === 14;
  results['blockpos'] = bp.center().x === 10.5 && bp.up(2).y === 66;
  } catch (e) {
    chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    chat.log('[E2E] ' + key + '=' + value);
  }
  chat.log('[E2E] s1v2-types: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
