let fired = false;
neo.events.on('game:start', () => {
  if (fired) return;
  fired = true;
  start();
});

async function start() {
  const results = {};
  const check = (key, value) => { results[key] = Boolean(value); };

  check('face.shapes',
      typeof neo.permissions.checkPermission === 'function'
      && typeof neo.permissions.requestPermission === 'function');

  // A fresh install expects false (declared fs, content untrusted); a
  // config that already trusts this content starts at true and the request
  // below resolves without a prompt. Both are valid initial states.
  const before = neo.permissions.checkPermission('fs');
  neo.chat.log('[E2E] permissions: initial fs=' + before);
  check('face.check-boolean', before === true || before === false);
  check('face.check-unknown', neo.permissions.checkPermission('bogus') === false);

  // Undeclared and unknown requests reject with the contract errors.
  let undeclaredRejected = false;
  try { await neo.permissions.requestPermission('net'); }
  catch (e) {
    undeclaredRejected = String(e.message || e).includes('not declared');
  }
  check('face.undeclared-rejects', undeclaredRejected);

  let unknownRejected = false;
  try { await neo.permissions.requestPermission('bogus'); }
  catch (e) {
    unknownRejected = String(e.message || e).includes('unknown permission');
  }
  check('face.unknown-rejects', unknownRejected);

  // The real request: a confirmation dialog pops (or a clickable chat line
  // on older nodes). Allow persists trust and flips the running script; a
  // deny/escape resolves false. Either decision is valid — the assertion
  // is that the gated fs face matches the decision.
  let granted = null;
  try { granted = await neo.permissions.requestPermission('fs'); }
  catch (e) { granted = 'error: ' + (e.message || e); }

  let fsCallable = null;
  try {
    await neo.fs.exists('neomacro.yml');
    fsCallable = true;
  } catch (e) {
    // Any failure that is not the permission contract still proves the
    // member is reachable (the gate no longer denies).
    fsCallable = !String(e.message || e).includes('permission');
  }
  neo.chat.log('[E2E] permissions: request resolved=' + granted
      + ' fs.callable=' + fsCallable);
  check('face.grant-consistency',
      (granted === true && fsCallable === true)
      || (granted === false && fsCallable === false));

  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] ' + key + '=' + value);
  }
  neo.chat.log('[E2E] permissions: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
