let fired = false;
neo.events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  results['getters'] = typeof neo.world.dimension === 'string'
      && typeof neo.world.time === 'number' && typeof neo.world.raining === 'boolean'
      && typeof neo.world.day === 'number' && typeof neo.world.thundering === 'boolean'
      && typeof neo.world.difficulty === 'string';
  } catch (e) {
    neo.chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] ' + key + '=' + value);
  }
  neo.chat.log('[E2E] s1v2-world: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
