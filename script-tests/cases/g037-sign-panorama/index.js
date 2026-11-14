// G-037 sign/panorama: writeSign answers false with no sign editor open,
// writeSignLine validates its index and rejects null even then, and
// takePanorama settles as a promise — a folder outside the macro root
// rejects with its guard message, while a real capture either resolves with
// the vanilla message (in-world run: panorama_0..5.png land below
// config/NeoMacros/macros/g037-panorama/screenshots/) or rejects with the
// no-world message on the title screen. Headless by construction: nothing
// here opens a screen or requires a world — see plan/reports/G-037.md for
// the manual runClient checklist.
let fired = false;
neo.events.on('game:start', () => {
  if (fired) return;
  fired = true;
  start();
});

function start() {
  const results = {};
  const check = (key, value, detail) => {
    results[key] = Boolean(value);
    if (!value) neo.chat.log('[E2E] g037.' + key + ' detail: ' + (detail ?? ''));
  };

  // The write face only lands in an open sign editor; nothing is open here.
  let editorAnswer = 'unset';
  try {
    editorAnswer = neo.player.writeSign('a', 'b', 'c', 'd');
    check('noEditorFourLine', editorAnswer === false, String(editorAnswer));
  } catch (e) {
    check('noEditorFourLine', false, String(e));
  }

  // The index is validated even without an editor, and the single-line form
  // does not accept null.
  let indexThrew = false;
  try { neo.player.writeSignLine(4, 'x'); } catch (e) { indexThrew = true; }
  check('indexGuard', indexThrew);
  let nullThrew = false;
  try { neo.player.writeSignLine(0, null); } catch (e) { nullThrew = true; }
  check('nullGuard', nullThrew);
  let closedLine = 'unset';
  try {
    closedLine = neo.player.writeSignLine(0, 'still-closed');
    check('noEditorLine', closedLine === false, String(closedLine));
  } catch (e) {
    check('noEditorLine', false, String(e));
  }

  // A folder outside the macro root rejects before the capture runs.
  neo.player.takePanorama('../escape').then(
      (msg) => check('folderGuard', false, 'resolved: ' + msg),
      (err) => check('folderGuard',
          String(err && err.message || err).indexOf('macro root') >= 0,
          String(err)));

  // The capture itself always settles: resolve with the vanilla message
  // (in-world), reject with the no-world message (title screen). Either
  // way the script keeps running and the detail is printed for review.
  neo.player.takePanorama('g037-panorama').then(
      (msg) => {
        check('panoramaSettles', typeof msg === 'string' && msg.length > 0, msg);
        finish();
      },
      (err) => {
        const detail = String(err && err.message || err);
        check('panoramaSettles', detail.length > 0, detail);
        neo.chat.log('[E2E] g037 panorama rejected: ' + detail);
        finish();
      });
}

function finish() {
  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] g037.' + key + '=' + value);
  }
  neo.chat.log('[E2E] g037-sign-panorama: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
