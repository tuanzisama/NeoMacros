/**
 * E2E-G case T101 (migrated for the S1v2 unified live-handle reshape).
 * Mode: observe. Verifies Block/RayHit/neo.Entity handle shapes.
 */
let ticks = 0;
let done = false;

neo.events.on('tick', () => {
  ticks++;
  if (ticks === 40 && !done) {
    done = true;
    run();
  }
});

function run() {
  const self = neo.player.self;
  const results = {};

  results['self-handle'] = self !== undefined && self.name !== undefined
      && self.health !== undefined && self.valid;
  if (self !== undefined) {
    neo.chat.log(`[E2E] self: ${self.name} at ${self.pos.x.toFixed(1)},${self.pos.y.toFixed(1)},${self.pos.z.toFixed(1)} hp=${self.health}`);
    const snap = self.snapshot();
    results['self-snapshot'] = snap !== null && snap.uuid === self.uuid;

    // inRadius returns neo.Entity[] handles (not snapshots)
    const nearby = neo.entity.inRadius(self.pos.x, self.pos.y, self.pos.z, 32);
    neo.chat.log(`[E2E] entities within 32 blocks: ${nearby.length}`);
    results['radius-handles'] = Array.isArray(nearby)
        && (nearby.length === 0 || nearby[0] instanceof Object);
    results['nearest'] = neo.entity.nearest(self.pos.x, self.pos.y, self.pos.z, 32) !== undefined;
    results['players'] = Array.isArray(neo.entity.players());

    // blockAt returns a Block handle: state.id instead of .id
    const here = neo.world.blockAt(self.pos.x, self.pos.y - 1, self.pos.z);
    neo.chat.log(`[E2E] block below: ${here === undefined ? '(air/void)' : here.state.id}`);
    results['block-handle'] = here !== undefined && here.state !== undefined
        && typeof here.state.id === 'string';
    results['block-valid'] = here !== undefined && here.valid === true;

    // raycast returns a RayHit handle: .block or .entity + .pos + .distance
    const origin = self.pos;
    const look = self.direction;
    const hit = neo.world.raycast(origin.add(new neo.Pos3D(0, 1.6, 0)),
        look.scale(4), { maxDist: 4, entities: true, blocks: true });
    neo.chat.log(`[E2E] raycast: ${hit === undefined ? '(no hit fn)' : hit.hit ? 'HIT @ ' + hit.distance.toFixed(1) : 'miss fn returned'}`);
    results['raycast-hits'] = hit !== undefined && typeof hit.distance === 'number';
  } else {
    neo.chat.log('[E2E] self handle unavailable (not in a world?)');
  }

  const pass = Object.values(results).every(Boolean);
  neo.chat.log(`[E2E] T101-entity-lookup: ${pass ? 'PASS' : 'FAIL'} (observe; verify printed values against the world)`);
}
