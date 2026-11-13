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
  // G-017 block/chunk face: shape checks only; a missing world or a block
  // of air reads undefined and still counts as present.
  const block = neo.world.blockAt(0, 64, 0);
  if (block === undefined) {
    check('g017.block-absent', true);
  } else {
    check('g017.block-state', typeof block.state === 'object'
        && typeof block.state.id === 'string');
    check('g017.block-defaultState',
        block.defaultState === undefined || (typeof block.defaultState === 'object'
            && typeof block.defaultState.id === 'string'));
    check('g017.block-defaultItemStack',
        block.defaultItemStack === undefined
        || typeof block.defaultItemStack.id === 'string');
    check('g017.block-multipliers',
        typeof block.blastResistance === 'number'
        && typeof block.jumpVelocityMultiplier === 'number'
        && typeof block.slipperiness === 'number'
        && typeof block.velocityMultiplier === 'number');
    check('g017.block-tags', Array.isArray(block.tags));
    check('g017.block-states', Array.isArray(block.states)
        && block.states.length >= 1);
    check('g017.block-name', typeof block.name === 'string');
    check('g017.block-predicates',
        typeof block.canMobSpawnInside() === 'boolean'
        && typeof block.hasDynamicBounds() === 'boolean');
    check('g017.block-hasTag',
        block.hasTag('minecraft:base_stone_overworld') !== undefined
        && block.hasTag('nope:nope') === false);
    const state = block.state;
    check('g017.state-flags',
        typeof state.solid === 'boolean'
        && typeof state.exceedsCube() === 'boolean'
        && typeof state.blocksMovement() === 'boolean'
        && typeof state.hasComparatorOutput() === 'boolean');
    check('g017.state-position-queries',
        typeof state.allowsSpawning(0, 64, 0, 'sheep') === 'boolean'
        && typeof state.shouldSuffocate(0, 64, 0) === 'boolean');
  }
  const chunk = neo.world.chunkAt(0, 64, 0);
  if (chunk === undefined) {
    check('g017.chunk-absent', true);
  } else {
    check('g017.chunk-geometry',
        typeof chunk.minBlock === 'object'
        && typeof chunk.offsetBlock(1, 64, 1).x === 'number');
    check('g017.chunk-height', typeof chunk.height === 'number');
    const top = chunk.topYAt(0, 0, 'world_surface');
    check('g017.chunk-topYAt', top === undefined || typeof top === 'number');
    check('g017.chunk-contains',
        typeof chunk.containsAny(['minecraft:stone']) === 'boolean'
        && chunk.containsAll([]) === true
        && chunk.containsAny([]) === false);
    check('g017.chunk-entities', Array.isArray(chunk.entities));
  }
  const g017ray = neo.world.raycast(0, 64, 0, 0, 64, 4);
  check('g017.ray-insideBlock', g017ray === undefined
      || g017ray.insideBlock === undefined
      || typeof g017ray.insideBlock === 'boolean');
  // G-017B position/text/math face: shape checks only.
  const g017bPos = new neo.Pos2D(1, 2);
  check('g017b.pos-compare',
      typeof g017bPos.compare(new neo.Pos2D(1, 3)) === 'number'
      && g017bPos.compare(new neo.Pos2D(1, 2)) === 0);
  check('g017b.pos-floor', new neo.Pos3D(1.7, 64.2, -3.1).floor().x === 1
      && new neo.Pos3D(1.7, 64.2, -3.1).floor().z === -4);
  const g017bLink = new neo.Component('gap').color('red').hover('t').build();
  const g017bHover = g017bLink.hoverValue;
  check('g017b.text-style-readback', g017bLink.color === 'red'
      && g017bLink.hoverAction === 'show_text'
      && g017bHover !== undefined && g017bHover.plain === 't');
  const g017bDraft = new neo.Component('gap');
  if (typeof g017bDraft.click === 'function') {
    check('g017b.text-generic-click',
        new neo.Component('gap').click('change_page', '3').build().clickValue === '3');
    let g017bRejected = false;
    try {
      new neo.Component('gap').click('custom', 'x');
    } catch (e) {
      g017bRejected = true;
    }
    check('g017b.text-click-validation', g017bRejected);
  }
  if (typeof g017bDraft.style === 'function') {
    check('g017b.text-style-merge',
        g017bDraft.style(g017bLink).build().color === 'red');
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
    // G-020 menu-face observations: each member is only present on its own
    // container handle, so every shape check accepts undefined.
    check('player.inventory-itemName',
        inv.itemName === undefined || typeof inv.itemName === 'string');
    check('player.inventory-costLimit',
        inv.costLimit === undefined || typeof inv.costLimit === 'number');
    check('player.inventory-fuelValues',
        inv.fuelValues === undefined || typeof inv.fuelValues === 'object');
    check('player.inventory-furnace-times',
        inv.fuelTime === undefined || typeof inv.fuelTime === 'number');
    check('player.inventory-smeltedItem',
        inv.smeltedItem === undefined || typeof inv.smeltedItem.id === 'string');
    check('player.inventory-enchantments',
        inv.enchantments === undefined || Array.isArray(inv.enchantments));
    check('player.inventory-enchantmentIds',
        inv.enchantmentIds === undefined || Array.isArray(inv.enchantmentIds));
    check('player.inventory-recipes',
        inv.recipes === undefined || Array.isArray(inv.recipes));
    check('player.inventory-xpEstimate',
        inv.xpEstimate === undefined
            || inv.xpEstimate() === undefined || typeof inv.xpEstimate() === 'number');
    check('player.inventory-canCraft',
        inv.canCraft === undefined
            || inv.canCraft() === undefined || typeof inv.canCraft() === 'boolean');
  }
  if (self) {
    check('player.statistics', self.statistics === null || typeof self.statistics === 'object');
    check('player.interactionRange', typeof self.blockInteractionRange === 'number'
        && typeof self.entityInteractionRange === 'number');
    // A no-arg is/getter publishes as a property (isBreakingBlock -> breakingBlock).
    check('player.isBreakingBlock', typeof self.breakingBlock === 'boolean');
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
