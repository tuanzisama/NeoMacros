import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';
import { fs } from '@neomacros/fs';

let fired = false;
events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  await fs.writeText('e2e-tmp.txt', 'hello');
  const content = await fs.readText('e2e-tmp.txt');
  results['text'] = content === 'hello';
  await fs.writeJSON('e2e-tmp.json', { a: 1 });
  const j = await fs.readJSON('e2e-tmp.json');
  results['json'] = j.a === 1;
  const st = await fs.stat('e2e-tmp.txt');
  results['stat'] = st.isFile === true;
  await fs.delete('e2e-tmp.txt');
  await fs.delete('e2e-tmp.json');
  } catch (e) {
    chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    chat.log('[E2E] ' + key + '=' + value);
  }
  chat.log('[E2E] s1v2-fs: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
