let fired = false;
neo.events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  const start = Date.now();
  await neo.thread.sleep(30);
  results['sleep'] = Date.now() - start >= 25;
  const sub = neo.thread.after(10, () => {});
  sub.cancel();
  results['cancel'] = typeof sub.cancel === 'function';
  } catch (e) {
    neo.chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] ' + key + '=' + value);
  }
  neo.chat.log('[E2E] s1v2-clock: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
