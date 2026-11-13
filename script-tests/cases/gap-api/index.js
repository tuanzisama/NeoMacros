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
  const inv = neo.player.inventory;
  check('player.inventory-undefined-or-handle',
      inv === undefined || (typeof inv === 'object'
          && typeof inv.regionOf === 'function'
          && typeof inv.getSlotPos === 'function'
          && typeof inv.open === 'function'));
  if (inv) {
    check('player.inventory-slotUnderMouse',
        inv.slotUnderMouse === undefined || typeof inv.slotUnderMouse === 'number');
    check('player.inventory-containerId',
        inv.containerId === undefined || typeof inv.containerId === 'number');
  }
  if (self) {
    check('player.statistics', self.statistics === null || typeof self.statistics === 'object');
    check('player.interactionRange', typeof self.blockInteractionRange === 'number'
        && typeof self.entityInteractionRange === 'number');
    check('player.isBreakingBlock', typeof self.isBreakingBlock === 'boolean');
    const selfInv = self.inventory;
    check('player.self-inventory-craft-grid',
        typeof selfInv === 'object' && selfInv.craftingWidth === 2
        && selfInv.craftingHeight === 2 && selfInv.craftingSlotCount === 4
        && selfInv.inputSize === 4);
    check('player.self-inventory-isInHotbar',
        typeof selfInv === 'object' && selfInv.isInHotbar(3) === true
        && selfInv.isInHotbar(40) === true && selfInv.isInHotbar(9) === false);
    check('player.self-inventory-equipment',
        typeof selfInv === 'object'
        && (selfInv.offhand === undefined || typeof selfInv.offhand === 'object')
        && (selfInv.helmet === undefined || typeof selfInv.helmet === 'object'));
    check('player.self-inventory-recipeBook',
        typeof selfInv === 'object'
        && (selfInv.recipeBookOpened === undefined
            || typeof selfInv.recipeBookOpened === 'boolean'));
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
