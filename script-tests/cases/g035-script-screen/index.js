// G-035 script-built screen (player.buildScreen / BuiltScreen): shape,
// the seven control types, mutation round-trips, and the closed-handle
// rules. Runs headlessly by opening the screen and closing it again; the
// interactive items (Esc close, widget clicking, onRender visuals) stay on
// the manual runClient checklist — see plan/reports/G-035.md.
let fired = false;
neo.events.on('game:start', () => {
  if (fired) return;
  fired = true;
  start();
});

function start() {
  const results = {};
  const check = (key, value) => { results[key] = Boolean(value); };

  let gui;
  try {
    gui = neo.player.buildScreen({
      title: 'G035',
      showTitle: true,
      closeOnEsc: true,
      returnTo: 'current',
      controls: [
        { type: 'button', id: 'btn', x: 10, y: 40, width: 98, height: 20,
          label: 'Buy' },
        { type: 'imageButton', id: 'img', x: 120, y: 40, width: 20, height: 20,
          sprites: { enabled: 'minecraft:widgets/button.png',
              disabled: 'minecraft:widgets/button.png',
              enabledFocused: 'minecraft:widgets/button.png',
              disabledFocused: 'minecraft:widgets/button.png' } },
        { type: 'checkbox', id: 'chk', x: 10, y: 70, width: 98, height: 20,
          label: 'Flag', checked: true },
        { type: 'slider', id: 'sld', x: 10, y: 100, width: 98, height: 20,
          label: 'Amount', value: 0.5, steps: 4 },
        { type: 'editBox', id: 'txt', x: 10, y: 130, width: 98, height: 20,
          label: 'Name', text: 'seed' },
        { type: 'cycleButton', id: 'cyc', x: 10, y: 160, width: 98, height: 20,
          label: 'Mode', values: ['A', 'B', 'C'], initial: 'A' },
        { type: 'lockButton', id: 'lck', x: 120, y: 70 },
      ],
      onClose: () => { results.closed = true; },
    });
  } catch (e) {
    check('build', false);
    neo.chat.log('[E2E] g035 build failed: ' + e);
    return;
  }

  check('build', gui !== undefined && gui.title === 'G035'
      && gui.name === 'ScriptScreen' && gui.closed === false
      && gui.returnTo === 'current');
  check('controls', gui.controls.length === 7
      && gui.control('chk').checked === true
      && gui.control('sld').value === 0.5
      && gui.control('txt').text === 'seed'
      && gui.control('cyc').text === 'A');
  check('modifiers', typeof gui.shiftDown === 'boolean'
      && typeof gui.ctrlDown === 'boolean'
      && typeof gui.altDown === 'boolean');

  // Runtime callback swap and the whitelist patch face.
  gui.on('mouseDown', () => true);
  gui.add({ type: 'button', id: 'extra', x: 10, y: 190, width: 98, height: 20,
      label: 'More' });
  check('add', gui.controls.length === 8 && gui.control('extra') !== undefined);
  gui.setControl('chk', { checked: false });
  check('setControl', gui.control('chk').checked === false);
  gui.reload();
  check('reload', gui.closed === false && gui.controls.length === 8);
  gui.remove('extra');
  check('remove', gui.controls.length === 7
      && gui.control('extra') === undefined);

  let unknownThrew = false;
  try { gui.on('hover', () => {}); } catch (e) { unknownThrew = true; }
  check('unknownCallback', unknownThrew);

  gui.close();
  check('close', gui.closed === true && results.closed === true);

  let mutatorsThrew = false;
  try { gui.add({ type: 'button', id: 'late' }); } catch (e) { mutatorsThrew = true; }
  check('closedMutatorsThrow', mutatorsThrew && gui.controls.length === 0);

  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] g035.' + key + '=' + value);
  }
  neo.chat.log('[E2E] g035-script-screen: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
