import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';
import { thread } from '@neomacros/thread';
import { player } from '@neomacros/player';
import { Pos3D, ChunkPos, ItemStack, ItemStackBuilder, TickEvent } from '@neomacros/types';

const results = {};
let ready = false;

events.on('game:start', () => {
  if (ready) return; ready = true;
  main();
});

events.on('tick', (ev) => {
  if (!ready) return;
  results['baseEvent'] = ev instanceof TickEvent && ev.type === 'tick';
});

async function main() {
  try {
    const v = new Pos3D(1, 2, 3);
    const scaled = v.scale(2);
    const comp = v.mul(new Pos3D(2, 2, 2));
    results['pos3d'] = scaled.x === 2 && comp.y === 4 && v.distanceSq(new Pos3D(4, 6, 3)) === 9;
    const chunk = new ChunkPos(new Pos3D(160, 64, -32).floor());
    results['chunkPos'] = chunk.x === 10 && chunk.z === -2 && chunk.minX === 160;
    const sword = new ItemStackBuilder('diamond_sword')
      .name('Excalibur').enchant('sharpness', 5).count(1).build();
    results['itemStack'] = sword instanceof ItemStack
      && sword.id === 'minecraft:diamond_sword' && sword.isSameItem('diamond_sword');
    const timer = thread.after(50, () => {});
    results['thread'] = typeof timer.cancel === 'function' && thread.isMain() === false;
    results['opened'] = player.opened === undefined;
    await thread.sleep(10);
    results['sleep'] = true;
  } catch (e) {
    chat.log('[E2E] ERROR: ' + e);
  }
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    chat.log('[E2E] ' + key + '=' + value);
  }
  chat.log('[E2E] api2-smoke: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
