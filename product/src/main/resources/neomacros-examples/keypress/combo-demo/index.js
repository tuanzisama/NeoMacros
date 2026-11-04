// Combo keypress script: fires on the configured keydown edge while CTRL is
// held (see neomacro.yml). ev.modifiers lists every modifier held at that
// moment, so [ctrl] matches Ctrl+Shift+K too.
export function startup(ev) {
    neo.chat.log('[combo-demo] keydown ' + ev.key
        + ' with modifiers: ' + (ev.modifiers.length ? ev.modifiers.join('+') : 'none'));
}
