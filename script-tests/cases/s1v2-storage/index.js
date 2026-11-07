let fired = false;
neo.events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  neo.storage.put('k', { n: 1 });
  const v = neo.storage.get('k');
  results['roundtrip'] = v !== undefined && v.n === 1;
  neo.storage.remove('k');
  } catch (e) {
    neo.chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] ' + key + '=' + value);
  }
  neo.chat.log('[E2E] s1v2-storage: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
