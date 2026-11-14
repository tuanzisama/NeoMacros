package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.PacketRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-031 packet scenarios over the real engine and the stub adapter: the
 * receive payload shape with its lazy buffer, the cancellable send channel
 * with in-place rewrite verdicts, the codec-less {@code buffer: undefined}
 * path, wildcard isolation (L1), and the {@code neo.client} packet face.
 */
class PacketEventScenariosTest {

    @TempDir
    Path dir;

    /** Writes one script folder (manifest + index.js) below the event category. */
    private static void fixture(Path scriptsDir, String name, String source) throws Exception {
        String stem = name.endsWith(".js") ? name.substring(0, name.length() - 3) : name;
        Path folder = scriptsDir.resolve("event").resolve(stem);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("neomacro.yml"), String.join("\n",
                "name: " + stem,
                "version: 1.0.0",
                "author: e2e",
                "config:",
                "  event:",
                "    on: [game:start]",
                ""));
        Files.writeString(folder.resolve("index.js"), source + "\nexport function startup() {}\n");
    }

    private static TestBridge.Report require(E2ERuntime e2e, String key) {
        TestBridge.Report report = e2e.bridge().get(key);
        assertTrue(report != null && report.pass(),
                key + ": " + (report == null ? "missing" : report.detail())
                        + " | script errors: " + e2e.scriptErrors());
        return report;
    }

    @Test
    void receiveEventCarriesThePayloadShapeAndLazyBuffer() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "packet-rx.js", String.join("\n",
                    "neo.events.on('packet:receive', (ev) => {",
                    "  if (ev.id !== 'minecraft:keep_alive') { return; }",
                    "  const buffer = ev.buffer;",
                    "  neo.__test.report('packet-rx',",
                    "      ev.flow === 'clientbound' && ev.protocol === 'play'",
                    "          && buffer !== undefined && buffer !== null",
                    "          && buffer.readerIndex === 0 && buffer.writerIndex === 5",
                    "          && buffer.readByte() === 104,",
                    "    JSON.stringify({ id: ev.id, flow: ev.flow, protocol: ev.protocol,",
                    "        writerIndex: buffer ? buffer.writerIndex : null }));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("packet-rx.js"));

            e2e.adapter().clientPort().firePacketReceive(new PacketRef(
                    "minecraft:keep_alive", "clientbound", "play", "hello"));
            e2e.awaitSettle();
            require(e2e, "packet-rx");
        }
    }

    @Test
    void codeclessReceiveReportsUndefinedBuffer() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "packet-rx-none.js", String.join("\n",
                    "neo.events.on('packet:receive', (ev) => {",
                    "  if (ev.id !== 'minecraft:bundle_delimiter') { return; }",
                    "  neo.__test.report('packet-rx-none', ev.buffer === undefined,",
                    "    JSON.stringify({ buffer: ev.buffer }));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("packet-rx-none.js"));

            e2e.adapter().clientPort().packetEncodable = false;
            e2e.adapter().clientPort().firePacketReceive(new PacketRef(
                    "minecraft:bundle_delimiter", "clientbound", "play", "frame"));
            e2e.awaitSettle();
            require(e2e, "packet-rx-none");
        }
    }

    @Test
    void sendChannelCancelsAndRewritesThroughTheScript() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "packet-tx.js", String.join("\n",
                    "neo.events.onSync('packet:send', (ev) => {",
                    "  if (ev.id === 'minecraft:swing') { ev.cancel(); return; }",
                    "  if (ev.id === 'minecraft:chat') { ev.buffer.writeByte(88); }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("packet-tx.js"));

            var stub = e2e.adapter().clientPort();
            var canceled = stub.firePacketSend(new PacketRef(
                    "minecraft:swing", "serverbound", "play", "abc"));
            assertTrue(canceled.canceled(), "the script cancels the swing packet");
            assertNull(canceled.replacement());

            var rewritten = stub.firePacketSend(new PacketRef(
                    "minecraft:chat", "serverbound", "play", "abc"));
            assertFalse(rewritten.canceled(), "the chat packet passes");
            assertEquals("abcX", rewritten.replacement(),
                    "the in-place write re-decodes onto the wire");

            var pass = stub.firePacketSend(new PacketRef(
                    "minecraft:keep_alive", "serverbound", "play", "ping"));
            assertFalse(pass.canceled(), "untouched packets stay untouched");
            assertNull(pass.replacement());
        }
    }

    @Test
    void wildcardSubscribersNeverSeePacketEvents() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "packet-star.js", String.join("\n",
                    "let seen = 0;",
                    "neo.events.on('*', (ev) => {",
                    "  if (String(ev.type).indexOf('packet:') === 0) { seen++; }",
                    // A later non-packet event snapshots the counter: any
                    // packet leak into the wildcard would have landed first.
                    "  if (ev.type === 'input:wheel') {",
                    "    neo.__test.report('packet-star', seen === 0, 'seen=' + seen);",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("packet-star.js"));

            e2e.adapter().clientPort().firePacketReceive(new PacketRef(
                    "minecraft:keep_alive", "clientbound", "play", "hello"));
            e2e.adapter().clientPort().firePacketSend(new PacketRef(
                    "minecraft:swing", "serverbound", "play", "abc"));
            e2e.adapter().inputPort().onScrollFire(1.0);
            e2e.awaitSettle();
            require(e2e, "packet-star");
        }
    }

    @Test
    void clientPacketFaceExposesIdsAndBuilders() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "packet-face.js", String.join("\n",
                    "const ids = neo.client.packetIds;",
                    "const built = neo.client.createPacketBuffer();",
                    "built.writeInt(7);",
                    "built.setReaderIndex(0);",
                    "const roundTrip = built.readInt();",
                    "const root = new neo.PacketBuffer();",
                    "root.writeVarInt(300);",
                    "root.setReaderIndex(0);",
                    "neo.__test.report('packet-face',",
                    "    Array.isArray(ids) && ids.length === 2",
                    "        && typeof neo.client.sendPacket === 'function'",
                    "        && roundTrip === 7 && built.readerIndex === 4",
                    "        && root.readVarInt() === 300,",
                    "    JSON.stringify({ ids: ids, roundTrip: roundTrip }));",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("packet-face.js"));
            e2e.awaitSettle();
            require(e2e, "packet-face");
        }
    }
}
