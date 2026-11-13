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
  const opts = neo.client.options;
  check('client.options-handle',
      opts !== undefined && opts !== null && typeof opts === 'object'
          && typeof opts.setFov === 'function'
          && typeof opts.saveOptions === 'function');
  if (opts !== undefined && opts !== null) {
    check('client.options.root-read',
        opts.fov === undefined || typeof opts.fov === 'number');
    const video = opts.video;
    check('client.options.video-handle',
        video !== undefined && typeof video === 'object'
            && typeof video.setRenderDistance === 'function'
            && (video.renderDistance === undefined
                || typeof video.renderDistance === 'number')
            && (video.graphicsMode === undefined
                || typeof video.graphicsMode === 'string'));
    check('client.options.groups',
        opts.skin !== undefined && opts.music !== undefined
            && opts.control !== undefined && opts.chat !== undefined
            && opts.accessibility !== undefined);
    const chat = opts.chat;
    const control = opts.control;
    const a11y = opts.accessibility;
    check('client.options.chat-handle',
        typeof chat.setChatOpacity === 'function'
            && typeof chat.setNarrator === 'function'
            && (chat.chatOpacity === undefined
                || typeof chat.chatOpacity === 'number')
            && (chat.chatVisibility === undefined
                || typeof chat.chatVisibility === 'string'));
    check('client.options.control-handle',
        typeof control.setAutoJump === 'function'
            && typeof control.setSensitivity === 'function'
            && (control.sensitivity === undefined
                || typeof control.sensitivity === 'number')
            && (control.keys === undefined || Array.isArray(control.keys))
            && (control.categories === undefined
                || Array.isArray(control.categories))
            && (control.keyBindsByCategory === undefined
                || typeof control.keyBindsByCategory === 'object')
            && typeof control.getKeyBindsByCategory === 'function');
    check('client.options.accessibility-handle',
        typeof a11y.setShowSubtitles === 'function'
            && typeof a11y.setFovEffect === 'function'
            && (a11y.showSubtitles === undefined
                || typeof a11y.showSubtitles === 'boolean')
            && (a11y.hideLightningFlash === undefined
                || typeof a11y.hideLightningFlash === 'boolean'));
    try {
      const pending = opts.setFov(70);
      check('client.options.write-promise',
          Boolean(pending) && typeof pending.then === 'function');
      pending.catch(() => {});
    } catch (e) {
      check('client.options.write-promise', false);
    }
  }
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
  // G-022 species pose/display face: shape checks only; a world without
  // the species reads absent and still counts as present.
  const g022Center = self ? self.pos : { x: 0, y: 64, z: 0 };
  const g022Near = neo.entity.inRadius(
      g022Center.x, g022Center.y, g022Center.z, 48);
  const g022ByType = (id) => g022Near.find((e) => e.type === id);
  const g022Absent = (key) => check(key + '-absent', true);
  const g022Stand = g022ByType('minecraft:armor_stand');
  if (g022Stand) {
    check('g022.armorStand-flags',
        typeof g022Stand.invisible === 'boolean'
        && typeof g022Stand.showArms() === 'boolean'
        && typeof g022Stand.showBasePlate() === 'boolean');
    const g022Head = g022Stand.headPose;
    check('g022.armorStand-pose',
        typeof g022Head === 'object' && g022Head !== null
        && typeof g022Head.x === 'number'
        && typeof g022Head.y === 'number'
        && typeof g022Head.z === 'number'
        && typeof g022Stand.bodyPose.x === 'number'
        && typeof g022Stand.leftArmPose.y === 'number'
        && typeof g022Stand.rightLegPose.z === 'number');
  } else {
    g022Absent('g022.armorStand');
  }
  const g022Panda = g022ByType('minecraft:panda');
  if (g022Panda) {
    check('g022.panda-genes',
        typeof g022Panda.mainGene === 'string'
        && typeof g022Panda.hiddenGene === 'string'
        && typeof g022Panda.mainGeneRecessive === 'boolean'
        && typeof g022Panda.hiddenGeneRecessive === 'boolean');
  } else {
    g022Absent('g022.panda');
  }
  const g022BlockDisplay = g022ByType('minecraft:block_display');
  if (g022BlockDisplay) {
    check('g022.blockDisplay',
        typeof g022BlockDisplay.billboard === 'string'
        && typeof g022BlockDisplay.block === 'string'
        && typeof g022BlockDisplay.blockState === 'object'
        && g022BlockDisplay.blockState !== null
        && typeof g022BlockDisplay.blockState.id === 'string'
        && typeof g022BlockDisplay.blockState.props === 'object');
    const g022Box = g022BlockDisplay.visibilityBoundingBox;
    check('g022.blockDisplay-visibilityBox',
        typeof g022Box === 'object' && g022Box !== null
        && typeof g022Box.min === 'object' && typeof g022Box.max === 'object'
        && typeof g022Box.min.x === 'number' && typeof g022Box.max.y === 'number');
  } else {
    g022Absent('g022.blockDisplay');
  }
  const g022ItemDisplay = g022ByType('minecraft:item_display');
  if (g022ItemDisplay) {
    check('g022.itemDisplay',
        typeof g022ItemDisplay.transform === 'string'
        && typeof g022ItemDisplay.billboard === 'string');
  } else {
    g022Absent('g022.itemDisplay');
  }
  const g022TextDisplay = g022ByType('minecraft:text_display');
  if (g022TextDisplay) {
    check('g022.textDisplay-data',
        typeof g022TextDisplay.lineWidth === 'number'
        && typeof g022TextDisplay.textOpacity === 'number'
        && typeof g022TextDisplay.alignment === 'string'
        && typeof g022TextDisplay.hasShadow() === 'boolean'
        && typeof g022TextDisplay.hasSeeThrough() === 'boolean'
        && typeof g022TextDisplay.hasDefaultBackground() === 'boolean');
  } else {
    g022Absent('g022.textDisplay');
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
