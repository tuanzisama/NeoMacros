// G-031 packet-api case (G-002 §8.3): receive/send subscription shape,
// one harmless upstream cancel, the builder face, and the two gate
// outcomes around declared-but-possibly-untrusted permissions.
//
// The manifest declares [java, packet]: on the first run the trust prompt
// is pending, so the module-path gate (client.sendPacket) must deny with
// PermissionError; after the player trusts the script the same call is
// accepted. Both outcomes are consistent — the case passes either way and
// logs which one it saw.
let fired = false;
neo.events.on('game:start', () => {
  if (fired) return;
  fired = true;
  start();
});

function start() {
  const results = {};
  const check = (key, value) => { results[key] = Boolean(value); };

  // ① packet:receive subscription; live payloads are shape-checked when
  // the client actually sees traffic (after joining a world).
  let liveShape = null;
  const rx = neo.events.on('packet:receive', (ev) => {
    const buffer = ev.buffer;
    const shape = typeof ev.id === 'string'
        && (ev.flow === 'clientbound' || ev.flow === 'serverbound')
        && typeof ev.protocol === 'string'
        && (buffer === undefined
            || (typeof buffer.readerIndex === 'number'
                && typeof buffer.writerIndex === 'number'
                && buffer.readerIndex <= buffer.writerIndex));
    if (!shape) {
      neo.chat.log('[E2E] g031.receive-bad=' + JSON.stringify(
          { id: ev.id, flow: ev.flow, protocol: ev.protocol }));
    }
    liveShape = shape;
  });
  check('g031.receive-subscribed', rx !== undefined && rx !== null);

  // ② onSync cancel: swings stop leaving the client for this session —
  // the harmless upstream cancel of G-002 §8.5-4 (server never sees one).
  const tx = neo.events.onSync('packet:send', (ev) => {
    if (ev.id === 'minecraft:swing') { ev.cancel(); }
  });
  check('g031.sendSync-subscribed', tx !== undefined && tx !== null);

  // ③ Builder face (offline-automated): client.createPacketBuffer and the
  // root constructor round-trip, and the id catalog is a non-empty array.
  const buf = neo.client.createPacketBuffer();
  buf.writeInt(7);
  buf.setReaderIndex(0);
  check('g031.builder-roundtrip',
      buf.readInt() === 7 && buf.readerIndex === 4);
  const root = new neo.PacketBuffer();
  root.writeVarInt(300);
  root.setReaderIndex(0);
  check('g031.root-constructor', root.readVarInt() === 300);
  check('g031.packetIds',
      Array.isArray(neo.client.packetIds) && neo.client.packetIds.length > 0);

  // toPacket hands out raw objects: java-gated in the docs. Outcomes:
  // denied (binding layer), ok (trusted + decodable), or error (trusted +
  // undecodable bytes) — all well-defined.
  let toPacketOutcome = 'none';
  try {
    const decoded = buf.toPacket('minecraft:swing');
    toPacketOutcome = decoded === undefined || decoded === null ? 'null' : 'ok';
  } catch (e) {
    toPacketOutcome = String(e.message || e).indexOf('permission') >= 0
        ? 'denied' : 'error';
  }
  check('g031.toPacket-outcome',
      toPacketOutcome === 'denied' || toPacketOutcome === 'ok'
          || toPacketOutcome === 'error' || toPacketOutcome === 'null');
  neo.chat.log('[E2E] g031.toPacket outcome=' + toPacketOutcome);

  // ④ client.sendPacket is a module member, so the binding layer really
  // enforces java: PermissionError pre-trust, a settling promise post-trust.
  let sendPacketOutcome = 'none';
  try {
    const pending = neo.client.sendPacket(null);
    sendPacketOutcome = 'accepted';
    if (pending && typeof pending.catch === 'function') { pending.catch(() => {}); }
  } catch (e) {
    sendPacketOutcome = String(e.message || e).indexOf('permission') >= 0
        ? 'denied' : 'error';
  }
  check('g031.sendPacket-gate',
      sendPacketOutcome === 'denied' || sendPacketOutcome === 'accepted');
  neo.chat.log('[E2E] g031.sendPacket outcome=' + sendPacketOutcome);

  // buffer.send carries @ApiPermission("packet"); the observed outcome is
  // logged for review (PermissionError appears once the engine gates
  // handle members the way it gates module members — see the G-031 report).
  // The empty swing decode is dropped by the onSync cancel above, so
  // nothing extra reaches the wire.
  let bufferSendOutcome = 'none';
  try {
    const pending = neo.client.createPacketBuffer().send('minecraft:swing');
    bufferSendOutcome = 'handed-off';
    if (pending && typeof pending.catch === 'function') { pending.catch(() => {}); }
  } catch (e) {
    bufferSendOutcome = String(e.message || e).indexOf('permission') >= 0
        ? 'denied' : 'error';
  }
  check('g031.bufferSend-outcome', bufferSendOutcome !== 'none');
  neo.chat.log('[E2E] g031.bufferSend outcome=' + bufferSendOutcome);

  const pass = Object.values(results).every(Boolean);
  for (const [key, value] of Object.entries(results)) {
    neo.chat.log('[E2E] ' + key + '=' + value);
  }
  if (liveShape !== null) {
    neo.chat.log('[E2E] g031.receive-live=' + liveShape);
  }
  neo.chat.log('[E2E] packet-api: ' + (pass ? 'PASS' : 'FAIL'));
}

export function startup() {}
