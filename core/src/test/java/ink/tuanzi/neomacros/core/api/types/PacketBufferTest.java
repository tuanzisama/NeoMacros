package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.model.PacketRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PacketBuffer lifecycle and wire semantics (G-002 §4): the three-source
 * write-permission matrix, lazy materialization, index/mark/reset
 * behavior, scalar and structured round-trips over the vanilla network
 * encodings, and the toPacket/send decode face over a canned port.
 */
class PacketBufferTest {

    private enum Color {
        RED, GREEN, BLUE
    }

    /** Canned string codec: packets are plain UTF-8 payload text. */
    private static final class StubPort implements ClientPort {
        int encodeCalls;
        boolean failEncode;
        boolean failDecode;
        String lastDecodeId;
        String lastDecodeProtocol;
        String lastDecodeFlow;
        final List<Object> sent = new ArrayList<>();

        @Override
        public int fps() {
            return 0;
        }

        @Override
        public String gameVersion() {
            return "test";
        }

        @Override
        public Object minecraftInstance() {
            return null;
        }

        @Override
        public boolean canEncodePacket(Object packet, String protocol) {
            return packet instanceof String;
        }

        @Override
        public byte[] encodePacket(Object packet, String protocol) {
            if (failEncode) {
                throw new IllegalStateException("codec exploded");
            }
            encodeCalls++;
            return String.valueOf(packet).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object decodePacket(String packetId, String protocol, String flow,
                byte[] bytes, int offset) {
            if (failDecode) {
                throw new IllegalStateException("decode exploded");
            }
            lastDecodeId = packetId;
            lastDecodeProtocol = protocol;
            lastDecodeFlow = flow;
            return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
        }

        @Override
        public CompletableFuture<Void> sendRawPacket(Object packet) {
            sent.add(packet);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static PacketRef ref(String payload) {
        return new PacketRef("minecraft:chat", "serverbound", "play", payload);
    }

    // ------------------------------------------------------------------
    // Write-permission matrix (G-002 §4.4)
    // ------------------------------------------------------------------

    @Test
    void builderCellIsAlwaysWritableAndStartsEmpty() {
        PacketBuffer buffer = new PacketBuffer();
        assertEquals(0, buffer.getReaderIndex());
        assertEquals(0, buffer.getWriterIndex());
        buffer.writeInt(42);
        assertEquals(4, buffer.getWriterIndex());
        buffer.setReaderIndex(0);
        assertEquals(42, buffer.readInt());
        buffer.reset();
        assertEquals(0, buffer.getWriterIndex());
        assertArrayEquals(new byte[0], buffer.payloadBytes());
    }

    @Test
    void sendCellWriteWindowClosesAfterTheDispatch() {
        PacketBuffer buffer = PacketBuffer.sendCell(ref("seed"), new StubPort());
        buffer.writeByte('x');
        assertTrue(buffer.dirty());
        buffer.closeWindow();
        IllegalStateException raised = assertThrows(IllegalStateException.class,
                () -> buffer.writeByte('y'));
        assertEquals("packet buffer is read-only after the packet:send event",
                raised.getMessage());
        // Reads keep working after the window closes (G-002 §4.4).
        assertEquals('s', buffer.readByte());
        assertEquals(4, buffer.payloadBytes().length);
    }

    @Test
    void receiveCellIsAReadOnlyViewThatStillReadsForever() {
        PacketBuffer buffer = PacketBuffer.receiveView(ref("hello"), new StubPort());
        IllegalStateException raised = assertThrows(IllegalStateException.class,
                () -> buffer.writeByte(1));
        assertEquals("packet buffer is a read-only view (packet:receive)",
                raised.getMessage());
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8),
                buffer.payloadBytes());
        // A read never marks the cell dirty.
        assertFalse(buffer.dirty());
    }

    // ------------------------------------------------------------------
    // Lazy materialization (G-002 §4.1)
    // ------------------------------------------------------------------

    @Test
    void eventCellsMaterializeLazilyAndEncodeOnce() {
        StubPort port = new StubPort();
        PacketBuffer buffer = PacketBuffer.sendCell(ref("hello"), port);
        assertEquals(0, port.encodeCalls, "an untouched buffer never encodes");
        buffer.getWriterIndex();
        assertEquals(1, port.encodeCalls);
        buffer.getReaderIndex();
        buffer.payloadBytes();
        assertEquals(1, port.encodeCalls, "materialization happens once");
    }

    @Test
    void encodeFailureSurfacesAsAnEncodeError() {
        StubPort port = new StubPort();
        port.failEncode = true;
        PacketBuffer buffer = PacketBuffer.sendCell(ref("hello"), port);
        IllegalStateException raised = assertThrows(IllegalStateException.class,
                buffer::payloadBytes);
        assertTrue(raised.getMessage().startsWith("packet buffer encode failed: "),
                raised.getMessage());
    }

    // ------------------------------------------------------------------
    // Index / mark / reset surface
    // ------------------------------------------------------------------

    @Test
    void indexMarksAndClearFollowNettySemantics() {
        PacketBuffer buffer = new PacketBuffer();
        buffer.writeInt(7);
        buffer.writeInt(8);
        assertEquals(8, buffer.getWriterIndex());

        buffer.setReaderIndex(4);
        assertEquals(8, buffer.readInt());
        buffer.setReaderIndex(4);
        buffer.markReaderIndex();
        buffer.readInt();
        buffer.resetReaderIndex();
        assertEquals(4, buffer.getReaderIndex());

        buffer.markWriterIndex();
        buffer.writeByte(9);
        buffer.resetWriterIndex();
        assertEquals(8, buffer.getWriterIndex());

        buffer.clear();
        assertEquals(0, buffer.getReaderIndex());
        assertEquals(0, buffer.getWriterIndex());

        buffer.writeByte(1);
        buffer.writeByte(2);
        buffer.skipBytes(1);
        assertEquals(1, buffer.getReaderIndex());
        assertEquals(2, buffer.readByte());

        // Absolute reads are bounds-checked against capacity.
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.getBytes(99, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.setWriterIndex(99));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.setIndices(2, 1));
    }

    @Test
    void resetRestoresTheCapturedInitialBytesAndPointers() {
        StubPort port = new StubPort();
        PacketBuffer buffer = PacketBuffer.sendCell(ref("seed"), port);
        assertEquals("seed", new String(buffer.payloadBytes(), StandardCharsets.UTF_8));
        buffer.writeByte('!');
        buffer.setReaderIndex(2);
        buffer.reset();
        assertArrayEquals("seed".getBytes(StandardCharsets.UTF_8), buffer.payloadBytes());
        assertEquals(0, buffer.getReaderIndex());
        assertEquals(4, buffer.getWriterIndex());
    }

    // ------------------------------------------------------------------
    // Scalar round-trips
    // ------------------------------------------------------------------

    @Test
    void scalarRoundTripsMatchTheVanillaEncodings() {
        PacketBuffer buffer = new PacketBuffer();
        buffer.writeBoolean(true);
        buffer.writeByte(-5);
        buffer.writeShort(0x7FFF);
        buffer.writeMedium(0x123456);
        buffer.writeInt(-123456789);
        buffer.writeLong(0x0123456789ABCDEFL);
        buffer.writeFloat(3.5d);
        buffer.writeDouble(2.25d);
        buffer.writeChar(0x41);
        buffer.writeZero(2);

        buffer.setReaderIndex(0);
        assertTrue(buffer.readBoolean());
        assertEquals(-5, buffer.readByte());
        assertEquals(0x7FFF, buffer.readShort());
        assertEquals(0x123456, buffer.readMedium());
        assertEquals(-123456789, buffer.readInt());
        assertEquals(0x0123456789ABCDEFL, buffer.readLong());
        assertEquals(3.5d, buffer.readFloat());
        assertEquals(2.25d, buffer.readDouble());
        assertEquals(0x41, buffer.readChar());
        assertArrayEquals(new byte[2], buffer.readBytes(2));

        // Unsigned views and absolute access.
        buffer.clear();
        buffer.writeShort(-1);
        buffer.setReaderIndex(0);
        assertEquals(65535, buffer.readUnsignedShort());
        buffer.setReaderIndex(0);
        assertEquals(65535, buffer.getUnsignedShort(0));
        buffer.clear();
        buffer.writeInt(-1);
        buffer.setReaderIndex(0);
        assertEquals(0xFFFFFFFFL, buffer.readUnsignedInt());
        buffer.clear();
        buffer.writeMedium(0xFFFFFF);
        buffer.setReaderIndex(0);
        assertEquals(0xFFFFFF, buffer.readUnsignedMedium());

        // Big-endian layout of a known int.
        buffer.clear();
        buffer.writeInt(0x01020304);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, buffer.getBytes(0, 4));
    }

    @Test
    void varIntAndVarLongBoundariesMatchMojang() {
        PacketBuffer buffer = new PacketBuffer();
        int[] ints = {0, 1, 127, 128, -1, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (int value : ints) {
            buffer.writeVarInt(value);
        }
        buffer.setReaderIndex(0);
        for (int value : ints) {
            assertEquals(value, buffer.readVarInt());
        }

        buffer.clear();
        long[] longs = {0L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 1L << 40};
        for (long value : longs) {
            buffer.writeVarLong(value);
        }
        buffer.setReaderIndex(0);
        for (long value : longs) {
            assertEquals(value, buffer.readVarLong());
        }

        // The classic varint shape: 300 -> two bytes (0xAC 0x02).
        buffer.clear();
        buffer.writeVarInt(300);
        assertArrayEquals(new byte[]{(byte) 0xAC, 0x02}, buffer.getBytes(0, 2));
    }

    @Test
    void stringsUuidsAndIdentifiersRoundTrip() {
        PacketBuffer buffer = new PacketBuffer();
        buffer.writeString("héllo wörld");
        buffer.writeIdentifier("minecraft:stone");
        buffer.writeUuid("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        buffer.setReaderIndex(0);
        assertEquals("héllo wörld", buffer.readString());
        assertEquals("minecraft:stone", buffer.readIdentifier());
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", buffer.readUuid());

        assertThrows(IllegalArgumentException.class, () -> buffer.writeString("abcd", 3));
        assertThrows(IllegalArgumentException.class, () -> buffer.writeUuid("not-a-uuid"));
    }

    // ------------------------------------------------------------------
    // Arrays and positions
    // ------------------------------------------------------------------

    @Test
    void byteArrayInputsCoerceAndRoundTrip() {
        PacketBuffer buffer = new PacketBuffer();
        buffer.writeByteArray(List.of(1, 2, 3));
        buffer.writeByteArray(new byte[]{4, 5});
        buffer.setReaderIndex(0);
        assertArrayEquals(new byte[]{1, 2, 3}, buffer.readByteArray());
        assertArrayEquals(new byte[]{4, 5}, buffer.readByteArray());
        assertThrows(IllegalArgumentException.class,
                () -> new PacketBuffer().writeByteArray(List.of("nope")));

        PacketBuffer absolute = new PacketBuffer();
        absolute.writeZero(4);
        absolute.setBytes(1, List.of(7, 8));
        assertArrayEquals(new byte[]{0, 7, 8, 0}, absolute.getBytes(0, 4));
        assertArrayEquals(new byte[]{7, 8}, absolute.getBytes(1, 2));
    }

    @Test
    void intAndLongArraysRoundTripWithBounds() {
        PacketBuffer buffer = new PacketBuffer();
        buffer.writeIntList(List.of(1, -2, 300));
        buffer.writeLongArray(List.of(1L, -1L));
        buffer.setReaderIndex(0);
        assertEquals(List.of(1, -2, 300), buffer.readIntList());
        assertEquals(List.of(1L, -1L), buffer.readLongArray());

        buffer.clear();
        buffer.writeIntList(List.of(1, 2, 3));
        buffer.setReaderIndex(0);
        assertThrows(IllegalArgumentException.class, () -> buffer.readIntArray(2));
        assertThrows(IllegalArgumentException.class, () -> buffer.readByteArray(-1));
    }

    @Test
    void positionEncodingsRoundTrip() {
        PacketBuffer buffer = new PacketBuffer();
        buffer.writeBlockPos(-30000000, 64, 12345678);
        buffer.writeChunkPos(-5, 9);
        buffer.writeChunkSectionPos(-2, 4, 7);
        buffer.writeGlobalPos("minecraft:the_nether", 1, 2, 3);
        int dimensionBytes = 1 + "minecraft:the_nether".getBytes(StandardCharsets.UTF_8).length;
        assertEquals(8 + 8 + 8 + dimensionBytes + 8, buffer.getWriterIndex(),
                "packed longs plus the dimension string");

        buffer.setReaderIndex(0);
        BlockPos pos = buffer.readBlockPos();
        assertEquals(-30000000, pos.getIntX());
        assertEquals(64, pos.getIntY());
        assertEquals(12345678, pos.getIntZ());
        ChunkPos chunk = buffer.readChunkPos();
        assertEquals(-5, chunk.getX());
        assertEquals(9, chunk.getZ());
        BlockPos section = buffer.readChunkSectionPos();
        assertEquals(-2, section.getIntX());
        assertEquals(4, section.getIntY());
        assertEquals(7, section.getIntZ());
        // Global pos: dimension string then the packed position.
        assertEquals(24, buffer.getReaderIndex());
        assertEquals("minecraft:the_nether", buffer.readString());
        BlockPos global = buffer.readBlockPos();
        assertEquals(1, global.getIntX());
        assertEquals(2, global.getIntY());
        assertEquals(3, global.getIntZ());
    }

    // ------------------------------------------------------------------
    // Structured payloads
    // ------------------------------------------------------------------

    @Test
    void collectionMapAndOptionalRoundTripThroughCallbacks() {
        PacketBuffer buffer = new PacketBuffer();
        buffer.writeCollection(List.of("a", "bb"), (buf, item) -> buf.writeString(item));
        buffer.writeOptional(7, (buf, value) -> buf.writeInt(value));
        buffer.writeOptional(null, (buf, value) -> buf.writeInt(0));
        buffer.writeMap(Map.of("k", 1), (buf, key) -> buf.writeString(key),
                (buf, value) -> buf.writeInt(value));
        buffer.setReaderIndex(0);

        assertEquals(List.of("a", "bb"),
                buffer.readList(buf -> buf.readString()));
        Object present = buffer.readOptional(buf -> buf.readInt());
        assertEquals(7, present);
        assertNull(buffer.readOptional(buf -> buf.readInt()));
        List<List<Object>> pairs = buffer.readMap(buf -> buf.readString(),
                buf -> buf.readInt());
        assertEquals(1, pairs.size());
        assertEquals("k", pairs.get(0).get(0));
        assertEquals(1, pairs.get(0).get(1));

        buffer.clear();
        buffer.writeCollection(List.of(1, 2, 3), (buf, item) -> buf.writeVarInt(item));
        buffer.setReaderIndex(0);
        assertEquals(List.of(1, 2, 3), buffer.readAll(buf -> buf.readVarInt()));
    }

    @Test
    void enumSurfacesReadOrdinalsByName() {
        PacketBuffer buffer = PacketBuffer.builder(new StubPort());
        buffer.writeEnumConstant(Color.GREEN);
        buffer.setReaderIndex(0);
        assertEquals(1, buffer.readVarInt(), "enums encode as varint ordinals");
        buffer.setReaderIndex(0);
        assertEquals("GREEN",
                buffer.readEnum(PacketBufferTest.class.getName() + "$Color"));
        assertThrows(IllegalArgumentException.class, () -> buffer.readEnum("java.lang.String"));
        assertThrows(IllegalArgumentException.class,
                () -> buffer.writeEnumConstant("not-an-enum"));
    }

    @Test
    void blockHitResultRoundTripsBothForms() {
        PacketBuffer buffer = new PacketBuffer();
        buffer.writeBlockHitResult(Map.of(
                "pos", new BlockPos(1, 2, 3),
                "direction", "north",
                "offsetX", 0.5d, "offsetY", 0.0d, "offsetZ", 0.25d,
                "insideBlock", true, "worldBorder", false));
        buffer.setReaderIndex(0);
        Map<String, Object> read = buffer.readBlockHitResult();
        assertEquals(new BlockPos(1, 2, 3), read.get("pos"));
        assertEquals("north", read.get("direction"));
        assertEquals(0.5d, (Double) read.get("offsetX"), 0.0001);
        assertEquals(true, read.get("insideBlock"));
        assertEquals(false, read.get("worldBorder"));

        buffer.clear();
        buffer.writeBlockHitResult(new Pos3D(16.5, 64.0, -8.25d), "up",
                new BlockPos(16, 64, -9), false, false);
        buffer.setReaderIndex(0);
        Map<String, Object> second = buffer.readBlockHitResult();
        assertEquals(new BlockPos(16, 64, -9), second.get("pos"));
        assertEquals("up", second.get("direction"));
        assertEquals(0.5d, (Double) second.get("offsetX"), 0.0001);
        assertEquals(0.0d, (Double) second.get("offsetY"), 0.0001);
        assertEquals(0.75d, (Double) second.get("offsetZ"), 0.0001);
    }

    // ------------------------------------------------------------------
    // Decode / send face
    // ------------------------------------------------------------------

    @Test
    void toPacketPrefersTheSourceIdentityAndFallsBackToArguments() {
        StubPort port = new StubPort();
        PacketBuffer cell = PacketBuffer.sendCell(ref("payload"), port);
        cell.writeByte('!');
        Object decoded = cell.toPacket(null, null);
        assertEquals("payload!", decoded);
        assertEquals("minecraft:chat", port.lastDecodeId);
        assertEquals("play", port.lastDecodeProtocol);
        assertEquals("serverbound", port.lastDecodeFlow);

        cell.toPacket("minecraft:custom", "login");
        assertEquals("minecraft:custom", port.lastDecodeId);
        assertEquals("login", port.lastDecodeProtocol);
        assertNull(port.lastDecodeFlow, "an explicit name leaves the flow open");

        PacketBuffer builder = PacketBuffer.builder(port);
        builder.writeByte('b');
        IllegalStateException raised = assertThrows(IllegalStateException.class,
                () -> builder.toPacket(null, null));
        assertTrue(raised.getMessage().contains("no source packet"), raised.getMessage());
        builder.toPacket("minecraft:builder", null);
        assertEquals("minecraft:builder", port.lastDecodeId);
        assertEquals("play", port.lastDecodeProtocol, "builders default to play");
    }

    @Test
    void sendDecodesThenHandsThePacketToThePort() throws Exception {
        StubPort port = new StubPort();
        PacketBuffer cell = PacketBuffer.sendCell(ref("go"), port);
        cell.writeByte('!');
        cell.send(null).get(1, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(List.of("go!"), port.sent);
        assertEquals("minecraft:chat", port.lastDecodeId);
    }

    // ------------------------------------------------------------------
    // Declarations (gate + shape)
    // ------------------------------------------------------------------

    @Test
    void declarationsCarryThePacketGateAndHandleShape() throws Exception {
        ApiClass shape = PacketBuffer.class.getAnnotation(ApiClass.class);
        assertEquals("PacketBuffer", shape.name());
        assertEquals(ApiClassKind.HANDLE, shape.kind());
        assertEquals("net.minecraft.network.FriendlyByteBuf", shape.wraps());
        assertTrue(ink.tuanzi.neomacros.core.api.SelfConstructing.class
                .isAssignableFrom(PacketBuffer.class));

        Method send = PacketBuffer.class.getMethod("send", String.class);
        assertEquals("packet", send.getAnnotation(ApiPermission.class).value());
        Method toPacket = PacketBuffer.class.getMethod("toPacket", String.class, String.class);
        assertEquals("java", toPacket.getAnnotation(ApiPermission.class).value());

        Object constructed = new PacketBuffer().construct(new Object[0]);
        assertInstanceOf(PacketBuffer.class, constructed);
    }
}
