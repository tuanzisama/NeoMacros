// Keypress script: when the key from the manifest (or a binding changed in
// the panel) is pressed, NeoMacros calls the exported onKey(ev) on this
// script's event loop.
// ev = { key: 'key.keyboard.j', clicks: <press count> }
//
// Want to listen for more keys from code? Use neo.input:
//   neo.input.registerKeyPress('key.keyboard.r', () => { ... });

export function onKey(ev) {
    neo.chat.log('[hello-key] you pressed ' + ev.key);
}
