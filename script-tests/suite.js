/**
 * E2E-G runner (in-game). Mode: auto.
 *
 * Executes the embedded baseline cases, prints one result line per case plus
 * a final SUITE-DONE marker, persists the full result map via storage (the
 * gradle verifyGameE2E task reads it after the client exits), and reports a
 * synthetic 'suite-done' storage entry as the completion signal.
 *
 * Run protocol: load this file (lifecycle 'always'), let the client boot;
 * with -Dneomacros.e2e.quitOnDone=true the mod shuts the client down after
 * the suite finishes.
 */
export const meta = {
  name: 'e2e-suite',
  version: '1.0.0',
  lifecycle: 'always',
  permissions: [],
};

import { events, clock } from '@neomacros/core';
import { chat } from '@neomacros/chat';
import { storage } from '@neomacros/storage';

const results = {};
const record = (id, pass, detail) => {
  results[id] = { pass, detail: detail ?? '' };
  chat.log(`[E2E] ${id}: ${pass ? 'PASS' : 'FAIL'}`);
};

events.on('game:start', () => {
  record('game-start', true, 'received');
});

let ticks = 0;
events.on('tick', () => {
  ticks++;
  if (ticks === 20) record('tick-20', true, 'ticks counted');
  if (ticks === 40) {
    events.onSync('chat:send', (ev) => ev.message === '__e2e-cancel-probe');
    clock.sleep(0).then(() => finish());
  }
});

function finish() {
  try {
    const pass = Object.values(results).every(r => r.pass);
    results['suite-done'] = { pass, detail: `${Object.keys(results).length} cases` };
    chat.log(`[E2E] suite done: ${pass ? 'ALL PASS' : 'FAILURES'} (${Object.keys(results).length})`);
    try {
      storage.put('e2e-results', results);
      chat.log('[E2E] storage saved');
    } catch (e) {
      chat.log('[E2E] storage error: ' + e);
    }
  } catch (e) {
    chat.log('[E2E] finish error: ' + e);
  }
}
