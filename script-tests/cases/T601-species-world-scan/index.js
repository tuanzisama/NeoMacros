/**
 * E2E-G case T601. Mode: observe (needs a real world session).
 *
 * Verifies the species-state batch and the world/entity scan surface:
 * - species handles dispatch to the deepest registered class, and the new
 *   gap-batch members read live values without throwing;
 * - world.findBlocks accepts id globs, state filters, and the sphere shape;
 * - world.scanBlocks resolves a promise with block handles;
 * - entity.inRadius type filters and entity.inBox return handles.
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
  if (self === undefined) {
    neo.chat.log('[E2E] self unavailable (not in a world?)');
    return;
  }
  const results = {};
  const cx = Math.floor(self.pos.x);
  const cy = Math.floor(self.pos.y);
  const cz = Math.floor(self.pos.z);

  // Species handles: the nearest mob (if any) must be a real ES class from
  // the neo root, and the new gap-batch getters must be readable.
  const mobs = neo.entity.inRadius(self.pos.x, self.pos.y, self.pos.z, 32);
  neo.chat.log(`[E2E] nearby handles: ${mobs.length}`);
  results['handle-array'] = Array.isArray(mobs);
  if (mobs.length > 0) {
    const mob = mobs[0];
    results['handle-type'] = typeof mob.type === 'string';
    results['handle-class'] = mob.constructor !== undefined
        && mob.constructor.name !== 'Object';
    neo.chat.log(`[E2E] sample ${mob.type} (${mob.constructor && mob.constructor.name})`);
    // Read the gap-batch members by type; undefined is acceptable when the
    // class has no such member or the entity is not that species.
    if (mob instanceof neo.SheepEntity) {
      results['sheep-members'] = typeof mob.shearable === 'boolean'
          && typeof mob.jeb === 'boolean';
    }
    if (mob instanceof neo.TamableAnimalEntity) {
      results['tameable-members'] = typeof mob.tamed === 'boolean'
          && typeof mob.sitting === 'boolean';
    }
    if (mob instanceof neo.PandaEntity) {
      results['panda-members'] = typeof mob.sitting === 'boolean'
          && typeof mob.rolling === 'boolean';
    }
    if (mob instanceof neo.AbstractIllagerEntity) {
      results['illager-armpose'] = typeof mob.armPose === 'string';
    }
  }

  // Entity scans: type filter plus the box query.
  const pigs = neo.entity.inRadius(self.pos.x, self.pos.y, self.pos.z, 32,
      'minecraft:pig');
  results['radius-type-filter'] = Array.isArray(pigs)
      && pigs.every((e) => e.type === 'minecraft:pig');
  const boxed = neo.entity.inBox({
    min: { x: cx - 16, y: cy - 16, z: cz - 16 },
    max: { x: cx + 16, y: cy + 16, z: cz + 16 },
  });
  neo.chat.log(`[E2E] inBox hits: ${boxed.length}`);
  results['inbox'] = Array.isArray(boxed) && boxed.length <= mobs.length;

  // Synchronous scan with explicit filters.
  const logs = neo.world.findBlocks({
    idPattern: 'minecraft:*_log',
    states: { axis: 'y' },
    center: { x: cx, y: cy, z: cz },
    range: 16,
    limit: 32,
  });
  neo.chat.log(`[E2E] findBlocks(pattern+states): ${logs.length}`);
  results['findblocks-filters'] = Array.isArray(logs)
      && logs.every((b) => b.state.id.endsWith('_log')
          && b.state.props.axis === 'y');

  const spherical = neo.world.findBlocks({
    types: 'minecraft:stone',
    center: { x: cx, y: cy, z: cz },
    range: 8,
    shape: 'sphere',
  });
  results['findblocks-sphere'] = Array.isArray(spherical);

  // Sliced async scan: the promise resolves with block handles.
  neo.world.scanBlocks({
    types: ['minecraft:dirt', 'minecraft:grass_block'],
    center: { x: cx, y: cy, z: cz },
    range: 24,
    limit: 256,
  }).then((swept) => {
    neo.chat.log(`[E2E] scanBlocks hits: ${swept.length}`);
    results['scanblocks-promise'] = Array.isArray(swept)
        && swept.every((b) => b.state !== undefined);
    finish(results);
  }).catch((err) => {
    neo.chat.log(`[E2E] scanBlocks rejected: ${err}`);
    results['scanblocks-promise'] = false;
    finish(results);
  });
}

function finish(results) {
  const pass = Object.values(results).every(Boolean);
  neo.chat.log(`[E2E] T601-species-world-scan: ${pass ? 'PASS' : 'FAIL'}`
      + ` (observe; verify printed values against the world)`);
}
