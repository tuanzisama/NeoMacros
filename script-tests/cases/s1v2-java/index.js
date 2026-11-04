import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';
import { java } from '@neomacros/java';

let fired = false;
events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  const MathCls = java.type('java.lang.Math');
  results['static'] = MathCls.PI > 3.14 && MathCls.floor(3.7) === 3;
  const list = java.type('java.util.ArrayList').new();
  list.add('a'); list.add('b');
  results['interop'] = list.size() === 2 && list.get(0) === 'a';
  } catch (e) {
    chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    chat.log('[E2E] ' + key + '=' + value);
  }
  chat.log('[E2E] s1v2-java: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
