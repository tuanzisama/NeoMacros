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
  // G-027 item face: shape checks over a builder stack. Offline facts and
  // unsupported queries read undefined; every face accepts undefined or its
  // designed type.
  const g027 = new neo.ItemStackBuilder('minecraft:diamond_sword').build();
  check('g027.stack-tool',
      g027.tool === undefined || typeof g027.tool === 'boolean');
  check('g027.stack-wearable',
      g027.wearable === undefined || typeof g027.wearable === 'boolean');
  check('g027.stack-fireproof',
      g027.fireproof === undefined || typeof g027.fireproof === 'boolean');
  check('g027.stack-blockItem',
      g027.blockItem === undefined || typeof g027.blockItem === 'boolean');
  check('g027.stack-block',
      g027.block === undefined || typeof g027.block.id === 'string');
  check('g027.stack-enchantability',
      g027.enchantability === undefined || typeof g027.enchantability === 'number');
  check('g027.stack-canFitInsideContainerItems',
      g027.canFitInsideContainerItems() === undefined
          || typeof g027.canFitInsideContainerItems() === 'boolean');
  check('g027.stack-hidden-flags',
      (g027.enchantmentsHidden === undefined
          || typeof g027.enchantmentsHidden === 'boolean')
      && (g027.modifiersHidden === undefined
          || typeof g027.modifiersHidden === 'boolean')
      && (g027.unbreakableHidden === undefined
          || typeof g027.unbreakableHidden === 'boolean')
      && (g027.canDestroyHidden === undefined
          || typeof g027.canDestroyHidden === 'boolean')
      && (g027.canPlaceHidden === undefined
          || typeof g027.canPlaceHidden === 'boolean')
      && (g027.dyeHidden === undefined || typeof g027.dyeHidden === 'boolean'));
  check('g027.stack-restrictions',
      (g027.hasDestroyRestrictions() === undefined
          || typeof g027.hasDestroyRestrictions() === 'boolean')
      && (g027.destroyRestrictions === undefined
          || Array.isArray(g027.destroyRestrictions))
      && (g027.hasPlaceRestrictions() === undefined
          || typeof g027.hasPlaceRestrictions() === 'boolean')
      && (g027.placeRestrictions === undefined
          || Array.isArray(g027.placeRestrictions)));
  check('g027.stack-creativeTab',
      g027.creativeTab === undefined || Array.isArray(g027.creativeTab));
  check('g027.stack-groupIcon',
      g027.groupIcon === undefined || Array.isArray(g027.groupIcon));
  check('g027.stack-tags',
      g027.tags === undefined || Array.isArray(g027.tags));
  check('g027.stack-defaultStack',
      g027.defaultStack === undefined
          || typeof g027.defaultStack.id === 'string');
  check('g027.stack-remainder',
      g027.recipeRemainder === undefined
          || typeof g027.recipeRemainder.id === 'string');
  check('g027.stack-hasRecipeRemainder',
      g027.hasRecipeRemainder() === undefined
          || typeof g027.hasRecipeRemainder() === 'boolean');
  check('g027.stack-attackDamage',
      g027.attackDamage === undefined || typeof g027.attackDamage === 'number');
  check('g027.stack-cooldown',
      (g027.onCooldown === undefined || typeof g027.onCooldown === 'boolean')
      && (g027.cooldownProgress === undefined
          || typeof g027.cooldownProgress === 'number'));
  const g027Material = new neo.ItemStackBuilder('minecraft:iron_ingot').build();
  check('g027.stack-repair',
      g027.canBeRepairedWith(g027Material) === undefined
          || typeof g027.canBeRepairedWith(g027Material) === 'boolean');
  const g027Block = neo.world.blockAt(0, 64, 0);
  if (g027Block === undefined) {
    check('g027.stack-tool-evaluation', true);
  } else {
    const g027Speed = g027.getDestroySpeed(g027Block.state);
    const g027Suit = g027.isCorrectToolForDrops(g027Block);
    check('g027.stack-tool-evaluation',
        (g027Speed === undefined || typeof g027Speed === 'number')
        && (g027Suit === undefined || typeof g027Suit === 'boolean'));
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
    // G-021 specialized-face observations: each member is only present on
    // its own container handle, so every shape check accepts undefined.
    check('player.inventory-patterns',
        inv.patterns === undefined || Array.isArray(inv.patterns));
    check('player.inventory-selectPattern',
        inv.selectPattern === undefined || typeof inv.selectPattern === 'function');
    check('player.inventory-selectTrade',
        inv.selectTrade === undefined || typeof inv.selectTrade === 'function');
    check('player.inventory-canRestock',
        inv.canRestock === undefined || typeof inv.canRestock === 'function');
    check('player.inventory-leveled',
        inv.leveled === undefined || typeof inv.leveled === 'boolean');
    check('player.inventory-mount-state',
        inv.canBeSaddled === undefined || typeof inv.canBeSaddled === 'function');
    check('player.inventory-saddled',
        inv.saddled === undefined || typeof inv.saddled === 'boolean');
    check('player.inventory-horse',
        inv.horse === undefined || typeof inv.horse === 'object');
    check('player.inventory-horseInventory',
        inv.horseInventory === undefined || Array.isArray(inv.horseInventory));
    check('player.inventory-tabNames',
        inv.tabNames === undefined || Array.isArray(inv.tabNames));
    check('player.inventory-tabTexts',
        inv.tabTexts === undefined || Array.isArray(inv.tabTexts));
    check('player.inventory-shownItems',
        inv.shownItems === undefined || Array.isArray(inv.shownItems));
    check('player.inventory-getSavedHotbar',
        inv.getSavedHotbar === undefined || typeof inv.getSavedHotbar === 'function');
    check('player.inventory-creative-actions',
        (inv.scroll === undefined || typeof inv.scroll === 'function')
        && (inv.search === undefined || typeof inv.search === 'function')
        && (inv.selectTab === undefined || typeof inv.selectTab === 'function'));
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
  // G-023 species small-gap face: shape checks per species; a world
  // without the species reads absent and still counts as present.
  const g023ByType = (id) => g022Near.find((e) => e.type === id);
  const g023Absent = (key) => check(key + '-absent', true);
  const g023First = (...ids) => ids.map(g023ByType).find(Boolean);
  const g023Wither = g023ByType('minecraft:wither');
  if (g023Wither) {
    check('g023.wither',
        typeof g023Wither.powered === 'boolean'
        && typeof g023Wither.invulnerableTicks === 'number');
  } else {
    g023Absent('g023.wither');
  }
  const g023Enderman = g023ByType('minecraft:enderman');
  if (g023Enderman) {
    const g023Held = g023Enderman.heldBlock;
    check('g023.enderman',
        typeof g023Enderman.provoked === 'boolean'
        && (g023Held === undefined || g023Held === null
            || (typeof g023Held.id === 'string'
                && typeof g023Held.props === 'object')));
  } else {
    g023Absent('g023.enderman');
  }
  const g023Guardian = g023First('minecraft:guardian', 'minecraft:elder_guardian');
  if (g023Guardian) {
    check('g023.guardian', typeof g023Guardian.moving === 'boolean');
  } else {
    g023Absent('g023.guardian');
  }
  const g023Illager = g023First('minecraft:vindicator', 'minecraft:pillager',
      'minecraft:evoker');
  if (g023Illager) {
    check('g023.illager',
        typeof g023Illager.celebrating === 'boolean'
        && typeof g023Illager.armPose === 'string');
  } else {
    g023Absent('g023.illager');
  }
  const g023Falling = g023ByType('minecraft:falling_block');
  if (g023Falling) {
    const g023State = g023Falling.blockState;
    const g023Start = g023Falling.startPos;
    check('g023.fallingBlock',
        typeof g023State === 'object' && g023State !== null
        && typeof g023State.id === 'string'
        && typeof g023Start === 'object' && g023Start !== null
        && typeof g023Start.x === 'number');
  } else {
    g023Absent('g023.fallingBlock');
  }
  const g023Horse = g023First('minecraft:horse', 'minecraft:donkey',
      'minecraft:mule', 'minecraft:llama');
  if (g023Horse) {
    check('g023.horse', typeof g023Horse.standing === 'boolean');
  } else {
    g023Absent('g023.horse');
  }
  const g023Chested = g023First('minecraft:donkey', 'minecraft:mule',
      'minecraft:llama');
  if (g023Chested) {
    check('g023.chestedHorse', typeof g023Chested.hasChest() === 'boolean');
  } else {
    g023Absent('g023.chestedHorse');
  }
  const g023Axolotl = g023ByType('minecraft:axolotl');
  if (g023Axolotl) {
    check('g023.axolotl',
        typeof g023Axolotl.variantId === 'number'
        && typeof g023Axolotl.variant === 'string');
  } else {
    g023Absent('g023.axolotl');
  }
  const g023Fish = g023ByType('minecraft:tropical_fish');
  if (g023Fish) {
    check('g023.tropicalFish',
        typeof g023Fish.size === 'string'
        && typeof g023Fish.varietyId === 'number'
        && typeof g023Fish.pattern === 'string');
  } else {
    g023Absent('g023.tropicalFish');
  }
  const g023Wolf = g023ByType('minecraft:wolf');
  if (g023Wolf) {
    check('g023.wolf', typeof g023Wolf.begging === 'boolean');
  } else {
    g023Absent('g023.wolf');
  }
  const g023Boat = g023First('minecraft:oak_boat', 'minecraft:oak_chest_boat',
      'minecraft:bamboo_raft');
  if (g023Boat) {
    const g023BoatItem = g023Boat.boatItem;
    check('g023.boat',
        typeof g023Boat.underWater === 'boolean'
        && (g023BoatItem === undefined || g023BoatItem === null
            || (typeof g023BoatItem.id === 'string'
                && typeof g023BoatItem.count === 'number')));
  } else {
    g023Absent('g023.boat');
  }
  const g023Animal = g023First('minecraft:cow', 'minecraft:sheep', 'minecraft:pig');
  if (g023Animal) {
    check('g023.animal',
        typeof g023Animal.isFood('minecraft:wheat') === 'boolean'
        && typeof g023Animal.canBreedWith(g023Animal) === 'boolean');
  } else {
    g023Absent('g023.animal');
  }
  const g023Cloud = g023ByType('minecraft:area_effect_cloud');
  if (g023Cloud) {
    check('g023.effectCloud',
        typeof g023Cloud.color === 'number'
        && typeof g023Cloud.particle === 'string');
  } else {
    g023Absent('g023.effectCloud');
  }
  const g023Shulker = g023ByType('minecraft:shulker');
  if (g023Shulker) {
    check('g023.shulker',
        typeof g023Shulker.peek === 'number'
        && typeof g023Shulker.attachFace === 'string');
  } else {
    g023Absent('g023.shulker');
  }
  const g023Drowned = g023ByType('minecraft:drowned');
  if (g023Drowned) {
    const g023Hand = g023Drowned.heldItem;
    check('g023.drowned', g023Hand === undefined || g023Hand === null
        || typeof g023Hand.is('minecraft:trident') === 'boolean');
  } else {
    g023Absent('g023.drowned');
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
