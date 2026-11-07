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
  check('screen.opened-null-or-object',
      neo.screen.opened === null || typeof neo.screen.opened === 'object');
  check('chat.getWidth', typeof neo.chat.getWidth('hello') === 'number');
  const self = neo.player.self;
  check('player.self-null-or-handle', self === undefined || typeof self === 'object');
  if (self) {
    check('player.statistics', self.statistics === null || typeof self.statistics === 'object');
    check('player.interactionRange', typeof self.blockInteractionRange === 'number'
        && typeof self.entityInteractionRange === 'number');
    check('player.isBreakingBlock', typeof self.isBreakingBlock === 'boolean');
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
