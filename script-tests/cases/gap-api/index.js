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
