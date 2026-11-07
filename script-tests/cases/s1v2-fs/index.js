let fired = false;
neo.events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  await neo.fs.writeText('e2e-tmp.txt', 'hello');
  const content = await neo.fs.readText('e2e-tmp.txt');
  results['text'] = content === 'hello';
  await neo.fs.writeJSON('e2e-tmp.json', { a: 1 });
  const j = await neo.fs.readJSON('e2e-tmp.json');
  results['json'] = j.a === 1;
  const st = await neo.fs.stat('e2e-tmp.txt');
  results['stat'] = st.isFile === true;
  await neo.fs.delete('e2e-tmp.txt');
  await neo.fs.delete('e2e-tmp.json');
  } catch (e) {
    neo.chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] ' + key + '=' + value);
  }
  neo.chat.log('[E2E] s1v2-fs: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
