const results = {};
let ready = false;

neo.events.on('game:start', () => {
  if (ready) return; ready = true;
  main();
});

neo.events.on('tick', (ev) => {
  if (!ready) return;
  results['baseEvent'] = ev instanceof neo.TickEvent && ev.type === 'tick';
});

async function main() {
  try {
    const v = new neo.Pos3D(1, 2, 3);
    const scaled = v.scale(2);
    const comp = v.mul(new neo.Pos3D(2, 2, 2));
    results['pos3d'] = scaled.x === 2 && comp.y === 4 && v.distanceSq(new neo.Pos3D(4, 6, 3)) === 9;
    const chunk = new neo.ChunkPos(new neo.Pos3D(160, 64, -32).floor());
    results['chunkPos'] = chunk.x === 10 && chunk.z === -2 && chunk.minX === 160;
    const sword = new neo.ItemStackBuilder('diamond_sword')
      .name('Excalibur').enchant('sharpness', 5).count(1).build();
    results['itemStack'] = sword instanceof neo.ItemStack
      && sword.id === 'minecraft:diamond_sword' && sword.isSameItem('diamond_sword');
    const timer = neo.thread.after(50, () => {});
    results['thread'] = typeof timer.cancel === 'function' && neo.thread.isMain() === false;
    results['opened'] = neo.player.inventory === undefined
      || neo.player.inventory.kind === 'player';
    await neo.thread.sleep(10);
    results['sleep'] = true;
  } catch (e) {
    neo.chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] ' + key + '=' + value);
  }
  neo.chat.log('[E2E] api2-smoke: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
