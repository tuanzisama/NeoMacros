/**
 * E2E-G runner (in-game). Mode: auto.
 *
 * Executes the embedded baseline cases, prints one result line per case plus
 * a final SUITE-DONE marker, persists the full result map via storage (the
 * gradle verifyGameE2E task reads it after the client exits), and reports a
 * synthetic 'suite-done' storage entry as the completion signal.
 *
 * Run protocol: install this folder below macros/services (the manifest
 * declares lifecycle 'always'), let the client boot; with
 * -Dneomacros.e2e.quitOnDone=true the mod shuts the client down after
 * the suite finishes.
 */
const results = {};
const record = (id, pass, detail) => {
  results[id] = { pass, detail: detail ?? '' };
  neo.chat.log(`[E2E] ${id}: ${pass ? 'PASS' : 'FAIL'}`);
};

neo.events.on('game:start', () => {
  record('game-start', true, 'received');
});

let ticks = 0;
neo.events.on('tick', () => {
  ticks++;
  if (ticks === 20) record('tick-20', true, 'ticks counted');
  if (ticks === 30) {
    neo.thread.sleep(50).then(
      () => record('sleep-settles', true, 'sleep(50) resolved'),
      (e) => record('sleep-settles', false, 'rejected: ' + e));
  }
  if (ticks === 60) {
    try {
      neo.events.onSync('chat:send', (ev) => false);
      record('onsync-register', true, 'registered');
    } catch (e) {
      record('onsync-register', false, String(e));
    }
  }
  if (ticks === 80) finish();
});

// T-101b mixin event family coverage
neo.events.once('entity:load', (ev) => {
  record('entity-load', true, 'entity loaded');
});
neo.events.once('world:sound', (ev) => {
  record('world-sound', true, 'sound: ' + ev.soundId);
});
neo.events.once('entity:unload', (ev) => {
  record('entity-unload', true, 'entity unloaded');
});

function finish() {
  try {
    const pass = Object.values(results).every(r => r.pass);
    results['suite-done'] = { pass, detail: `${Object.keys(results).length} cases` };
    neo.chat.log(`[E2E] suite done: ${pass ? 'ALL PASS' : 'FAILURES'} (${Object.keys(results).length})`);
    try {
      neo.storage.put('e2e-results', results);
      neo.chat.log('[E2E] storage saved');
    } catch (e) {
      neo.chat.log('[E2E] storage error: ' + e);
    }
  } catch (e) {
    neo.chat.log('[E2E] finish error: ' + e);
  }
}
