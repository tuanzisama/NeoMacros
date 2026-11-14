// G-036B secondary controls: the cycle/lock handles, the shared widget
// face (geometry/label/active/click/tooltips/parent extent), and the
// secondary builder family (CyclicButtonBuilder/LockButtonBuilder) with
// the completed AbstractWidgetBuilder base face. Headless by construction
// (open -> assert -> close); the interactive items (real widget presses,
// hover tooltips, the Alt-key list selector) stay on the manual runClient
// checklist — see plan/reports/G-036B.md.
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
      title: 'G036B',
      controls: [
        { type: 'cycleButton', id: 'mode', x: 10, y: 40, width: 98,
          height: 20, values: ['a', 'b', 'c'], initial: 'b',
          prefix: 'Mode: ' },
        { type: 'lockButton', id: 'gate', x: 10, y: 70, width: 16,
          height: 16 },
        { type: 'button', id: 'buy', x: 10, y: 100, width: 98, height: 20,
          label: 'Buy' },
      ],
    });
  } catch (e) {
    check('build', false);
    neo.chat.log('[E2E] g036b build failed: ' + e);
    return;
  }

  // Typed accessors bind by type; a mismatch throws, unknown answers
  // undefined, and a closed session drops every binding.
  let mismatch = false;
  try { gui.cycleButton('gate'); } catch (e) { mismatch = true; }
  check('accessors', gui.cycleButton('mode') !== undefined
      && gui.lockButton('gate') !== undefined
      && gui.button('buy') !== undefined
      && gui.cycleButton('missing') === undefined
      && mismatch);

  // CycleButton: value reads seed from the initial option; setValue
  // writes straight (unchanged answers true); cycle stays a transient op.
  const mode = gui.cycleButton('mode');
  check('cycleRead', mode.value === 'b' && mode.stringValue === 'b');
  check('cycleSet', mode.setValue('c') === false && mode.value === 'c'
      && mode.setValue('c') === true);
  mode.cycle(1);
  check('cycleTransient', mode.value === 'c');

  // LockButton: locked state round-trips through setControl.
  const gate = gui.lockButton('gate');
  check('lockRead', gate.locked === false);
  gate.setLocked(true);
  check('lockWrite', gate.locked === true
      && gui.control('gate').locked === true);

  // Shared widget face: geometry, label, active, layout reads and the
  // move/label writes round-trip into the control snapshot.
  const buy = gui.button('buy');
  check('widgetRead', buy.x === 10 && buy.y === 100 && buy.width === 98
      && buy.height === 20 && buy.message === 'Buy' && buy.active === true
      && buy.scaledWidth === 98 && buy.scaledLeft === 10
      && buy.scaledTop === 100 && buy.zIndex === 0);
  check('widgetParent', buy.parentWidth > 0 && buy.parentHeight > 0);
  buy.setPos(11, 101);
  buy.setMessage('Buy!');
  buy.setActive(false);
  check('widgetWrite', buy.x === 11 && buy.y === 101
      && buy.message === 'Buy!' && buy.active === false
      && gui.control('buy').x === 11);
  buy.click();

  // Tooltips: session-held list with replace/add/remove semantics.
  buy.setTooltip(['tip one', 'tip two']);
  buy.addTooltip('tip three');
  check('tooltipList', buy.tooltips.length === 3
      && buy.tooltips[0] === 'tip one');
  check('tooltipRemove', buy.removeTooltip(1) === true
      && buy.removeTooltip('tip three') === true
      && buy.removeTooltip('nope') === false
      && buy.tooltips.length === 1);

  // Secondary builders: option lists, build-time value labels, the
  // alternate-list toggle, alpha, and the lock seed/callback.
  let cyclicSpec;
  try {
    cyclicSpec = new neo.CyclicButtonBuilder()
        .id('mode2').pos(10, 130).size(98, 20)
        .values(['x', 'y']).alternatives(['p', 'q']).initially('y')
        .option('Mode: ').omitTextOption(true)
        .valueToText((v) => v.toUpperCase())
        .alternateToggle(() => true)
        .alpha(0.5)
        .onChange(() => {})
        .build();
    gui.add(cyclicSpec);
    gui.add(new neo.LockButtonBuilder()
        .id('gate2').pos(10, 160).size(16, 16).locked(true)
        .onClick(() => {})
        .build());
    gui.add(new neo.ButtonBuilder()
        .id('b2').pos(10, 190).size(98, 20)
        .alpha(0.5).moveTo(12, 192).build());
    check('builderBuild', true);
  } catch (e) {
    check('builderBuild', false);
    neo.chat.log('[E2E] g036b builder failed: ' + e);
  }
  const mode2 = gui.cycleButton('mode2');
  const gate2 = gui.lockButton('gate2');
  const b2 = gui.button('b2');
  check('cyclicSpec', cyclicSpec !== undefined
      && cyclicSpec.type === 'cycleButton'
      && cyclicSpec.initial === 'y'
      && cyclicSpec.omitTextOption === true
      && cyclicSpec.alpha === 0.5
      && cyclicSpec.valueLabels.x === 'X'
      && cyclicSpec.alternateToggle !== undefined);
  check('cyclicHandle', mode2 !== undefined
      && mode2.value === 'y' && mode2.stringValue === 'Y');
  check('lockSpec', gate2 !== undefined && gate2.locked === true);
  check('baseBuilder', b2 !== undefined && b2.x === 12 && b2.y === 192);

  // Builder base face: alpha clamps into 0..1, the layout reads answer
  // the configured box, and the parent extent reads the window.
  const probe = new neo.ButtonBuilder().pos(1, 2).size(3, 4).alpha(2.0);
  check('baseFace', probe.scaledWidth === 3 && probe.scaledHeight === 4
      && probe.scaledLeft === 1 && probe.scaledTop === 2
      && probe.parentWidth > 0);
  check('alphaClamp', probe.build().alpha === 1.0);

  gui.close();
  check('closedHandles', gui.closed === true
      && gui.cycleButton('mode') === undefined
      && gui.lockButton('gate') === undefined
      && gui.button('buy') === undefined);

  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] g036b.' + key + '=' + value);
  }
  neo.chat.log('[E2E] g036b-screen-controls: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
