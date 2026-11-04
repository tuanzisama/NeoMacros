/**
 * E2E-G case T101 (migrated for the S1v2 unified live-handle reshape).
 * Mode: observe. Verifies Block/RayHit/Entity handle shapes.
 */
import { events } from '@neomacros/core';
import { world } from '@neomacros/world';
import { entity } from '@neomacros/entity';
import { player } from '@neomacros/player';
import { chat } from '@neomacros/chat';
import { Vec3 } from '@neomacros/types';

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
      && self.health !== undefined && self.valid;
  if (self !== undefined) {
    chat.log(`[E2E] self: ${self.name} at ${self.x.toFixed(1)},${self.y.toFixed(1)},${self.z.toFixed(1)} hp=${self.health}`);
    const snap = self.snapshot();
    results['self-snapshot'] = snap !== null && snap.uuid === self.uuid;

    // inRadius returns Entity[] handles (not snapshots)
    const nearby = entity.inRadius(self.x, self.y, self.z, 32);
    chat.log(`[E2E] entities within 32 blocks: ${nearby.length}`);
    results['radius-handles'] = Array.isArray(nearby)
        && (nearby.length === 0 || nearby[0] instanceof Object);
    results['nearest'] = entity.nearest(self.x, self.y, self.z, 32) !== undefined;
    results['players'] = Array.isArray(entity.players());

    // blockAt returns a Block handle: state.id instead of .id
    const here = world.blockAt(self.x, self.y - 1, self.z);
    chat.log(`[E2E] block below: ${here === undefined ? '(air/void)' : here.state.id}`);
    results['block-handle'] = here !== undefined && here.state !== undefined
        && typeof here.state.id === 'string';
    results['block-valid'] = here !== undefined && here.valid === true;

    // raycast returns a RayHit handle: .block or .entity + .pos + .distance
    const origin = self.pos;
    const look = self.lookVec;
    const hit = world.raycast(origin.add(new Vec3(0, 1.6, 0)),
        look.mul(4), { maxDist: 4, entities: true, blocks: true });
    chat.log(`[E2E] raycast: ${hit === undefined ? '(no hit fn)' : hit.hit ? 'HIT @ ' + hit.distance.toFixed(1) : 'miss fn returned'}`);
    results['raycast-hits'] = hit !== undefined && typeof hit.distance === 'number';
  } else {
    chat.log('[E2E] self handle unavailable (not in a world?)');
  }

  const pass = Object.values(results).every(Boolean);
  chat.log(`[E2E] T101-entity-lookup: ${pass ? 'PASS' : 'FAIL'} (observe; verify printed values against the world)`);
}
