/**
 * E2E-G case T101: world/entity/player lane in a real game. Mode: observe.
 *
 * Runs standalone (install below macros/event; the manifest declares
 * lifecycle 'world'). After entering a world it queries the local player
 * handle, the entity radius query, and a world block lookup, and prints one
 * result line per check plus a summary marker for the log-grep protocol.
 * Visual/manual comparison items are limited to the printed values matching
 * the observer's world.
 */
import { events } from '@neomacros/core';
import { world } from '@neomacros/world';
import { entity } from '@neomacros/entity';
import { player } from '@neomacros/player';
import { chat } from '@neomacros/chat';

let ticks = 0;
let done = false;

events.on('tick', () => {
  ticks++;
  if (ticks === 40 && !done) {
    done = true;
    run();
  }
});

function run() {
  const self = player.self;
  const results = {};

  results['self-handle'] = self !== undefined && self.name !== undefined
      && self.health !== undefined && self.isValid();
  if (self !== undefined) {
    chat.log(`[E2E] self: ${self.name} at ${self.x.toFixed(1)},${self.y.toFixed(1)},${self.z.toFixed(1)} hp=${self.health}`);
    const snapshot = self.snapshot();
    results['self-snapshot'] = snapshot !== null && snapshot.uuid === self.uuid;

    const nearby = entity.inRadius(self.x, self.y, self.z, 32);
    chat.log(`[E2E] entities within 32 blocks: ${nearby.length}`);
    results['radius-query'] = Array.isArray(nearby);

    const here = world.blockAt(self.x, self.y - 1, self.z);
    chat.log(`[E2E] block below: ${here === undefined ? '(air/void)' : here.id}`);
    results['block-lookup'] = true;

    const ahead = world.raycast(
        self.x, self.y + 1.6, self.z,
        self.x + 4 * Math.cos(-(self.look ? self.look.yaw : 0) * Math.PI / 180),
        self.y + 1.6, self.z + 4 * Math.sin(-(self.look ? self.look.yaw : 0) * Math.PI / 180));
    chat.log(`[E2E] raycast ahead: ${ahead === undefined ? '(miss)' : ahead.id + ' @ ' + ahead.pos.x.toFixed(1) + ',' + ahead.pos.y.toFixed(1) + ',' + ahead.pos.z.toFixed(1)}`);
    results['raycast-runs'] = true;
  } else {
    chat.log('[E2E] self handle unavailable (not in a world?)');
  }

  const pass = Object.values(results).every(Boolean);
  chat.log(`[E2E] T101-entity-lookup: ${pass ? 'PASS' : 'FAIL'} (observe; verify printed values against the world)`);
}
