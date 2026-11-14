// G-036 basic control handles (Checkbox/Slider/EditBox/Button bindings)
// and the builder family: typed-accessor rules, per-control state
// round-trips, builder chains producing laid-out specs, and the
// edit-box text filter install path. Headless by construction (open →
// assert → close); the interactive items (typing through the filter,
// widget clicks) stay on the manual runClient checklist — see
// plan/reports/G-036.md.
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
      title: 'G036',
      controls: [
        { type: 'button', id: 'buy', x: 10, y: 40, width: 98, height: 20,
          label: 'Buy' },
        { type: 'imageButton', id: 'icon', x: 120, y: 40, width: 20,
          height: 20,
          sprites: { enabled: 'minecraft:widgets/button.png',
              disabled: 'minecraft:widgets/button.png',
              enabledFocused: 'minecraft:widgets/button.png',
              disabledFocused: 'minecraft:widgets/button.png' } },
        { type: 'checkbox', id: 'dbg', x: 10, y: 70, width: 98, height: 20,
          checked: false },
        { type: 'slider', id: 'amt', x: 10, y: 100, width: 98, height: 20,
          value: 0.5, steps: 4 },
        { type: 'editBox', id: 'name', x: 10, y: 130, width: 98, height: 20,
          text: 'hi', maxLength: 8, editable: true },
      ],
    });
  } catch (e) {
    check('build', false);
    neo.chat.log('[E2E] g036 build failed: ' + e);
    return;
  }

  // Typed accessors bind by type; image buttons answer the Button face.
  check('accessors', gui.button('buy') !== undefined
      && gui.button('icon') !== undefined
      && gui.checkbox('dbg') !== undefined
      && gui.slider('amt') !== undefined
      && gui.editBox('name') !== undefined
      && gui.button('missing') === undefined);
  let mismatchThrew = false;
  try { gui.checkbox('buy'); } catch (e) { mismatchThrew = true; }
  check('typeMismatchThrows', mismatchThrew);

  // Checkbox: state reads, toggle, setChecked round-trips.
  const box = gui.checkbox('dbg');
  check('checkboxRead', box.checked === false);
  box.toggle();
  check('checkboxToggle', box.checked === true
      && gui.control('dbg').checked === true);
  box.setChecked(false);
  check('checkboxSet', box.checked === false);

  // Slider: value/steps reads and writes.
  const slider = gui.slider('amt');
  check('sliderRead', slider.value === 0.5 && slider.steps === 4);
  slider.setValue(0.25).setSteps(10);
  check('sliderWrite', slider.value === 0.25 && slider.steps === 10
      && gui.control('amt').value === 0.25
      && gui.control('amt').steps === 10);

  // EditBox: text/editable/maxLength round-trips plus cursor ops.
  const edit = gui.editBox('name');
  check('editRead', edit.value === 'hi' && edit.editable === true
      && edit.maxLength === 8);
  edit.setValue('yo').setEditable(false).setMaxLength(16);
  check('editWrite', edit.value === 'yo' && edit.editable === false
      && edit.maxLength === 16
      && gui.control('name').text === 'yo');
  edit.setSelection(0, 1);
  edit.setCursorPosition(1, null);
  edit.moveCursorToStart(null);
  edit.moveCursorToEnd(true);
  check('editCursor', edit.value === 'yo');
  edit.setEditable(true);
  edit.setFilter((text) => text.length <= 4);
  edit.resetFilter();
  check('editFilter', edit.value === 'yo');

  // Builders: chains lay out specs that add and stay typed.
  gui.add(new neo.ButtonBuilder()
      .id('more').pos(10, 160).size(98, 20).message('More').build());
  gui.add(new neo.CheckBoxBuilder()
      .id('chk').pos(10, 190).size(98, 20).checked(true).build());
  gui.add(new neo.SliderBuilder()
      .id('amt2').pos(10, 220).size(98, 20).steps(5).initially(0.5).build());
  gui.add(new neo.TextFieldBuilder()
      .id('note').pos(10, 250).size(98, 20).suggestion('hint').build());
  gui.add(new neo.TexturedButtonBuilder()
      .id('pic').pos(120, 220).size(16, 16)
      .enabledTexture('minecraft:widgets/button.png')
      .disabledTexture('minecraft:widgets/button.png')
      .enabledFocusedTexture('minecraft:widgets/button.png')
      .disabledFocusedTexture('minecraft:widgets/button.png').build());
  const more = gui.control('more');
  const chk = gui.control('chk');
  const amt2 = gui.control('amt2');
  const note = gui.control('note');
  const pic = gui.control('pic');
  check('builderLayout', more.x === 10 && more.y === 160
      && more.width === 98 && more.height === 20 && more.label === 'More');
  check('builderState', chk.checked === true
      && amt2.x === 10 && amt2.y === 220 && amt2.steps === 5
      && amt2.value === 0.5
      && note.type === 'editBox' && pic.type === 'imageButton');
  check('builderTyped', gui.checkbox('chk').checked === true
      && gui.slider('amt2').steps === 5
      && gui.button('pic') !== undefined);

  // A builder-built button keeps a working click callback.
  let clicks = 0;
  gui.add(new neo.ButtonBuilder()
      .id('cbtn').pos(10, 280).size(98, 20).message('Click')
      .onClick(() => { clicks++; }).build());
  check('builderCallback', gui.control('cbtn').label === 'Click'
      && clicks === 0);

  gui.close();
  check('closedHandles', gui.closed === true
      && gui.checkbox('dbg') === undefined
      && gui.button('buy') === undefined);

  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] g036.' + key + '=' + value);
  }
  neo.chat.log('[E2E] g036-screen-controls: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
