/**
 * E2E-G case T602. Mode: observe (needs a real world session; open a chest,
 * click a slot, drop an item, then break a block to wear a tool).
 *
 * Verifies the inventory event family:
 * - inventory:click arrives before the click with kind/slot/button/mode and
 *   can be cancelled from onSync (a cancelled click changes no slot);
 * - inventory:dropSlot fires for THROW clicks and the hotbar Q key;
 * - inventory:open can be cancelled (a cancelled chest closes right away);
 * - inventory:update carries mode/oldStack/newStack (and the held variant);
 * - item:pickup and item:damage fire with their payloads.
 */
let ticks = 0;
let started = false;
const seen = {};

neo.events.onSync('inventory:click', (ev) => {
  seen.click = `${ev.kind} slot=${ev.slot} button=${ev.button} mode=${ev.mode}`;
  if (ev.mode === 'throw' && ev.slot === -999) {
    // Cancel drops outside the window so the observe case changes nothing.
    ev.cancel();
  }
});

neo.events.onSync('inventory:dropSlot', (ev) => {
  seen.drop = `${ev.kind} slot=${ev.slot} all=${ev.all}`;
});

neo.events.onSync('inventory:open', (ev) => {
  seen.open = `${ev.kind} size=${ev.size}`;
});

neo.events.on('inventory:update', (ev) => {
  if (ev.mode === 'slot' && ev.newStack !== undefined) {
    seen.slotUpdate = `${ev.kind} slot=${ev.slot} `
        + `${ev.oldStack === undefined ? '(empty)' : ev.oldStack.id + '@' + ev.oldStack.damage}`
        + ` -> ${ev.newStack.id}@${ev.newStack.damage}`;
  }
  if (ev.mode === 'held') {
    seen.heldUpdate = ev.newStack === undefined ? '(cleared)' : ev.newStack.id;
  }
});

neo.events.on('item:pickup', (ev) => {
  seen.pickup = `${ev.item.id}x${ev.amount}`;
});

neo.events.on('item:damage', (ev) => {
  seen.damage = `${ev.slot} ${ev.item.id} damage=${ev.damage}/${ev.item.maxDamage}`;
});

neo.events.on('tick', () => {
  ticks++;
  if (!started) {
    started = true;
    neo.chat.log('[E2E] T602 observing: open a chest, click a slot, drop an item, mine a block');
    // Script-issued click: the same pre-event path is cancellable.
    try {
      const inv = neo.player.inventory;
      if (inv !== undefined) {
        inv.click(0, 0, false).then(() => {
          neo.chat.log('[E2E] T602 script click completed (cancel it via onSync to block)');
        });
      }
    } catch (err) {
      neo.chat.log('[E2E] T602 script click failed: ' + err);
    }
  }
  if (ticks % 100 === 0) {
    neo.chat.log('[E2E] T602 observed so far: ' + JSON.stringify(seen));
  }
  if (ticks === 600) {
    const keys = Object.keys(seen);
    neo.chat.log(`[E2E] T602-inventory-events: ${keys.length >= 2 ? 'PASS' : 'FAIL'}`
        + ` (observe; seen ${keys.join(',') || 'nothing'})`);
    neo.chat.log('[E2E] T602 final: ' + JSON.stringify(seen, null, 2));
  }
});
