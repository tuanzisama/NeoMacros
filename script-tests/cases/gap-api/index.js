import { events } from '@neomacros/core';
import { chat } from '@neomacros/chat';
import { world } from '@neomacros/world';
import { client } from '@neomacros/client';
import { player } from '@neomacros/player';
import { input } from '@neomacros/input';
import { screen } from '@neomacros/screen';
import { fs } from '@neomacros/fs';

let fired = false;
events.on('game:start', () => {
  if (fired) return;
  fired = true;
  start();
});

function start() {
  const results = {};
  const check = (key, value) => { results[key] = Boolean(value); };

  check('world.moonPhase', typeof world.moonPhase === 'number');
  check('world.levelName', typeof world.levelName === 'string');
  check('world.tabList-null-or-object',
      world.tabList === null || typeof world.tabList === 'object');
  check('world.bossbars-array', Array.isArray(world.bossbars));
  check('world.scoreboards-null-or-object',
      world.scoreboards === null || typeof world.scoreboards === 'object');
  check('client.mods-array', Array.isArray(client.mods));
  check('client.isDevEnv', typeof client.isDevEnv === 'boolean');
  check('client.window', client.window === null || typeof client.window === 'object');
  check('client.mouse', typeof client.mouse === 'object');
  check('client.clipboard', typeof client.clipboard === 'string');
  check('input.keybinds-array', Array.isArray(input.keybinds));
  check('input.pressedKeys-array', Array.isArray(input.pressedKeys));
  check('input.movementState', typeof input.movementState === 'object');
  const moveSub = input.movement({ forward: true });
  moveSub.cancel();
  check('input.movement-subscription', typeof moveSub.cancel === 'function');
  check('screen.opened-null-or-object',
      screen.opened === null || typeof screen.opened === 'object');
  check('chat.getWidth', typeof chat.getWidth('hello') === 'number');
  const self = player.self;
  check('player.self-null-or-handle', self === undefined || typeof self === 'object');
  if (self) {
    check('player.statistics', self.statistics === null || typeof self.statistics === 'object');
    check('player.interactionRange', typeof self.blockInteractionRange === 'number'
        && typeof self.entityInteractionRange === 'number');
    check('player.isBreakingBlock', typeof self.isBreakingBlock === 'boolean');
  }
  try {
    const handle = fs.open('neomacro-test-gap.txt');
    check('fs.open-handle', typeof handle.readBytes === 'function'
        && typeof handle.attributes === 'function');
  } catch (e) {
    check('fs.open-handle', false);
  }

  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    chat.log('[E2E] ' + key + '=' + value);
  }
  chat.log('[E2E] gap-api: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
