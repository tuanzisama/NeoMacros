// Keypress script: when the configured trigger fires, NeoMacros calls the
// exported startup(ev) on this script's event loop.
// ev = {
//   event: 'keypress' | 'keydown' | 'keyup',   // the configured edge
//   key: 'key.keyboard.j',                     // the effective binding
//   code: 74,                                  // raw GLFW key code
//   clicks: <press count>,                     // press-edge click count
//   modifiers: ['ctrl', 'shift'],              // modifiers held right now
// }
//
// Want to listen for more keys from code? Use neo.input:
//   neo.input.registerKeyPress('key.keyboard.r', () => { ... });

export function startup(ev) {
    neo.chat.log('[hello-key] you pressed ' + ev.key);
}
