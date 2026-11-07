let fired = false;
neo.events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  const all = neo.entity.players();
  results['players'] = Array.isArray(all);
  const near = neo.entity.inRadius(new neo.Pos3D(0, 64, 0), 64);
  results['radius'] = Array.isArray(near);
  } catch (e) {
    neo.chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] ' + key + '=' + value);
  }
  neo.chat.log('[E2E] s1v2-entity: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
