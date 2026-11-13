let fired = false;
neo.events.on('game:start', () => {
  if (fired) return;
  fired = true;
  start();
});

function start() {
  const results = {};
  const check = (key, value) => { results[key] = Boolean(value); };

  check('world.moonPhase', typeof neo.world.moonPhase === 'number');
  check('world.levelName', typeof neo.world.levelName === 'string');
  check('world.tabList-null-or-object',
      neo.world.tabList === null || typeof neo.world.tabList === 'object');
  check('world.bossbars-array', Array.isArray(neo.world.bossbars));
  check('world.scoreboards-null-or-object',
      neo.world.scoreboards === null || typeof neo.world.scoreboards === 'object');
  const respawn = neo.world.respawnPos;
  check('world.respawnPos-absent-or-handle',
      respawn === undefined || (typeof respawn === 'object'
          && typeof respawn.x === 'number'
          && typeof respawn.y === 'number'
          && typeof respawn.z === 'number'));
  const light = neo.world.lightAt(0, 64, 0);
  check('world.lightAt-shape',
      light === undefined || (typeof light === 'object'
          && typeof light.sky === 'number' && typeof light.block === 'number'));
  try {
    const pending = neo.world.playSoundFile('neomacros-gap-api-missing.wav', 1);
    check('world.playSoundFile-promise',
        Boolean(pending) && typeof pending.then === 'function');
    // a deliberately missing file rejects; the rejection is the assertion
    pending.catch(() => {});
  } catch (e) {
    check('world.playSoundFile-promise', false);
  }
  check('client.mods-array', Array.isArray(neo.client.mods));
  check('client.isDevEnv', typeof neo.client.isDevEnv === 'boolean');
  check('client.window', neo.client.window === null || typeof neo.client.window === 'object');
  check('client.mouse', typeof neo.client.mouse === 'object');
  check('client.clipboard', typeof neo.client.clipboard === 'string');
  check('input.keybinds-array', Array.isArray(neo.input.keybinds));
  check('input.pressedKeys-array', Array.isArray(neo.input.pressedKeys));
  check('input.movementState', typeof neo.input.movementState === 'object');
  const moveSub = neo.input.movement({ forward: true });
  moveSub.cancel();
  check('input.movement-subscription', typeof moveSub.cancel === 'function');
  check('player.screen-null-or-object',
      neo.player.screen === null || typeof neo.player.screen === 'object');
  check('chat.getWidth', typeof neo.chat.getWidth('hello') === 'number');
  const self = neo.player.self;
  check('player.self-null-or-handle', self === undefined || typeof self === 'object');
  if (self) {
    check('player.statistics', self.statistics === null || typeof self.statistics === 'object');
    check('player.interactionRange', typeof self.blockInteractionRange === 'number'
        && typeof self.entityInteractionRange === 'number');
    // A no-arg is/getter publishes as a property (isBreakingBlock -> breakingBlock).
    check('player.isBreakingBlock', typeof self.breakingBlock === 'boolean');
    const absent = (v) => v === undefined || typeof v === 'boolean';
    check('entity.glowing', absent(self.glowing));
    check('entity.glowColor', self.glowColor === undefined
        || typeof self.glowColor === 'number');
    check('entity.customName', self.customName === undefined
        || typeof self.customName === 'string');
    check('entity.customNameVisible', absent(self.customNameVisible));
    check('entity.air', self.air === undefined || typeof self.air === 'number');
    check('entity.maxAir', self.maxAir === undefined || typeof self.maxAir === 'number');
    check('entity.speed', self.speed === undefined || typeof self.speed === 'number');
    const slot = (s) => s === undefined || typeof s.id === 'string';
    check('entity.armor-slots',
        slot(self.headArmor) && slot(self.chestArmor)
        && slot(self.legArmor) && slot(self.footArmor));
    check('entity.living-traits',
        (self.armorToughness === undefined || typeof self.armorToughness === 'number')
        && (self.defaultMaxHealth === undefined
            || typeof self.defaultMaxHealth === 'number')
        && (self.bowPull === undefined || typeof self.bowPull === 'number')
        && absent(self.breathesUnderwater) && absent(self.noDrag)
        && absent(self.partOfGame) && absent(self.undead) && absent(self.baby));
    check('entity.predicates',
        typeof self.canBeAffected('speed') === 'boolean'
        && typeof self.canAttack(self) === 'boolean'
        && typeof self.hasLineOfSight(self) === 'boolean');
    try {
      // The forced glow is restored right away, so the run ends on the
      // game's own decision.
      const pending = self.setGlowing(true).then(() => self.resetGlowing());
      check('entity.write-promise',
          Boolean(pending) && typeof pending.then === 'function');
      pending.catch(() => {});
    } catch (e) {
      check('entity.write-promise', false);
    }
  }
  try {
    const handle = neo.fs.open('neomacro-test-gap.txt');
    check('fs.open-handle', typeof handle.readBytes === 'function'
        && typeof handle.attributes === 'function');
  } catch (e) {
    check('fs.open-handle', false);
  }

  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] ' + key + '=' + value);
  }
  neo.chat.log('[E2E] gap-api: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
