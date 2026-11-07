let fired = false;
neo.events.on('game:start', () => {
  if (fired) return; fired = true;
  main();
});

async function main() {
  const results = {};
  try {
  const v = new neo.Pos3D(1, 2, 3);
  const w = new neo.Pos3D(4, 5, 6);
  const sum = v.add(w);
  const bp = new neo.BlockPos(10, 64, 200);
  results['vec3'] = sum.x === 5 && sum.y === 7 && sum.z === 9;
  results['lengthSq'] = v.lengthSq() === 14;
  results['blockpos'] = bp.center().x === 10.5 && bp.up(2).y === 66;
  } catch (e) {
    neo.chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] ' + key + '=' + value);
  }
  neo.chat.log('[E2E] s1v2-types: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
