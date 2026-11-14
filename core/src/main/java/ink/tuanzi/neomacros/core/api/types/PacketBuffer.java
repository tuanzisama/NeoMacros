package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.SelfConstructing;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.model.PacketRef;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Event-scoped packet byte buffer: the payload face of
 * {@code packet:receive} / {@code packet:send} and the free builder behind
 * {@code client.createPacketBuffer()} / {@code new neo.PacketBuffer()}.
 *
 * <p>Three sources share one implementation. A send-event cell starts
 * writable and its write window closes the moment the synchronous dispatch
 * returns (later writes raise, reads keep working forever); a receive-event
 * cell is a read-only view from the start; a builder cell is always
 * writable. Event cells materialize their canonical bytes lazily through the
 * client port's codec, so an unread buffer never encodes.</p>
 *
 * <p>All integers are big-endian and strings/varints follow the vanilla
 * network encodings, so bytes written here round-trip through the game's own
 * packet codecs.</p>
 *
 * @zh 事件作用域的包字节缓冲区：{@code packet:receive} / {@code packet:send} 的 payload 面，也是 {@code client.createPacketBuffer()} / {@code new neo.PacketBuffer()} 背后的自由构造器。三种来源共用一套实现：send 事件单元初始可写、同步派发一返回写窗口即关闭（之后写抛错，读永续可用）；receive 事件单元自始至只读；构造器单元恒可写。事件单元按需经客户端端口编解码物化规范字节，未读的缓冲区永不编码。全部整数为大端序，字符串与 varint 遵循原版网络编码，此处写入的字节可被游戏自身包编解码器回读。
 */
@ApiClass(name = "PacketBuffer", kind = ApiClassKind.HANDLE,
        wraps = "net.minecraft.network.FriendlyByteBuf")
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public final class PacketBuffer implements ScriptHandle, SelfConstructing {

    private static final byte[] EMPTY = new byte[0];
    private static final int MAX_STRING_CHARS = 32767;

    /** Direction {@code down, up, north, south, west, east} (vanilla order). */
    private static final String[] DIRECTIONS =
            {"down", "up", "north", "south", "west", "east"};

    private final ClientPort port;
    private final PacketRef source;
    private final String sourceId;
    private final String sourceFlow;
    private final String sourceProtocol;
    private final boolean receiveView;

    private byte[] data;
    private int readerIndex;
    private int writerIndex;
    private int markedReaderIndex;
    private int markedWriterIndex;
    private byte[] initialData;
    private int initialReaderIndex;
    private int initialWriterIndex;
    private boolean dirty;
    private boolean windowOpen;

    /** Creates an empty always-writable builder cell (script construction). */
    public PacketBuffer() {
        this.port = null;
        this.source = null;
        this.sourceId = null;
        this.sourceFlow = null;
        this.sourceProtocol = null;
        this.receiveView = false;
        this.windowOpen = false;
        this.data = EMPTY;
        this.initialData = EMPTY;
    }

    private PacketBuffer(ClientPort port, PacketRef source, boolean receiveView) {
        this.port = port;
        this.source = source;
        this.sourceId = source != null ? source.id() : null;
        this.sourceFlow = source != null ? source.flow() : null;
        this.sourceProtocol = source != null ? source.protocol() : null;
        this.receiveView = receiveView;
        this.windowOpen = !receiveView;
        // Event cells encode lazily; builders start empty.
        this.data = source != null ? null : EMPTY;
        this.initialData = source != null ? null : EMPTY;
    }

    /** Builder bound to an explicit port (host-side tests and wiring).
     * @zh 绑定显式端口的构造器（宿主侧测试与接线用）。 */
    @ApiInternal
    public static PacketBuffer builder(ClientPort port) {
        return new PacketBuffer(port, null, false);
    }

    /** Read-only cell wrapping one observed incoming packet.
     * @zh 包住一个观测到的下行包的只读单元。 */
    @ApiInternal
    public static PacketBuffer receiveView(PacketRef source, ClientPort port) {
        return new PacketBuffer(port, source, true);
    }

    /** Writable cell wrapping one outgoing packet for the sync window.
     * @zh 包住一个上行包、用于同步写窗口的可写单元。 */
    @ApiInternal
    public static PacketBuffer sendCell(PacketRef source, ClientPort port) {
        return new PacketBuffer(port, source, false);
    }

    /** Builds a fresh cell from raw guest constructor arguments.
     * @zh 由原始guest构造参数构建新单元。 */
    @Override
    public Object construct(Object[] args) {
        return new PacketBuffer();
    }

    // ------------------------------------------------------------------
    // Event-scope lifecycle hooks (wiring side; hidden from scripts)
    // ------------------------------------------------------------------

    /**
     * Closes the send-event write window: later writes raise, reads keep
     * working. Called by the wiring right after the synchronous dispatch.
     *
     * @zh 关闭 send 事件写窗口：之后写入抛错、读取照常。由接线在同步派发返回后立刻调用。
     */
    @ApiInternal
    public synchronized void closeWindow() {
        windowOpen = false;
    }

    /** Whether any write passed through this cell.
     * @zh 该单元是否经历过任何写入。 */
    @ApiInternal
    public synchronized boolean dirty() {
        return dirty;
    }

    /**
     * The readable payload {@code [readerIndex, writerIndex)} as a fresh
     * copy, for the adapter's re-decode step.
     *
     * @zh 可读载荷 {@code [readerIndex, writerIndex)} 的全新拷贝，供适配器重解码步骤使用。
     */
    @ApiInternal
    public synchronized byte[] payloadBytes() {
        ensureData();
        if (readerIndex >= writerIndex) {
            return EMPTY;
        }
        byte[] out = new byte[writerIndex - readerIndex];
        System.arraycopy(data, readerIndex, out, 0, out.length);
        return out;
    }

    // ------------------------------------------------------------------
    // Materialization and guards
    // ------------------------------------------------------------------

    private ClientPort port() {
        ClientPort resolved = port;
        if (resolved == null) {
            resolved = ScriptApiEnvironment.adapter().client();
        }
        return resolved;
    }

    private void ensureData() {
        if (data != null) {
            return;
        }
        byte[] encoded;
        try {
            encoded = port().encodePacket(source.packet(), sourceProtocol);
        } catch (Exception e) {
            String cause = e.getMessage() != null ? e.getMessage() : e.toString();
            throw new IllegalStateException("packet buffer encode failed: " + cause, e);
        }
        data = encoded != null ? encoded : EMPTY;
        readerIndex = 0;
        writerIndex = data.length;
        markedReaderIndex = 0;
        markedWriterIndex = 0;
        initialData = data.clone();
        initialReaderIndex = readerIndex;
        initialWriterIndex = writerIndex;
    }

    private void checkWritable() {
        if (receiveView) {
            throw new IllegalStateException(
                    "packet buffer is a read-only view (packet:receive)");
        }
        if (source != null && !windowOpen) {
            throw new IllegalStateException(
                    "packet buffer is read-only after the packet:send event");
        }
        dirty = true;
    }

    private void ensureCapacity(int writerPosition) {
        if (writerPosition <= data.length) {
            return;
        }
        int capacity = Math.max(writerPosition, data.length * 2 + 8);
        data = java.util.Arrays.copyOf(data, capacity);
    }

    private void checkRelative(int length) {
        if (length < 0 || readerIndex + length > writerIndex) {
            throw new IndexOutOfBoundsException("read " + length
                    + " bytes past readable region (readerIndex=" + readerIndex
                    + ", writerIndex=" + writerIndex + ")");
        }
    }

    private void checkAbsolute(int index, int length) {
        if (index < 0 || length < 0 || index + length > data.length) {
            throw new IndexOutOfBoundsException("index " + index + " + " + length
                    + " outside buffer capacity " + data.length);
        }
    }

    // ------------------------------------------------------------------
    // Raw big-endian primitives (caller holds the monitor)
    // ------------------------------------------------------------------

    private void writeByteRaw(int value) {
        ensureCapacity(writerIndex + 1);
        data[writerIndex++] = (byte) value;
    }

    private int readByteRaw() {
        checkRelative(1);
        return data[readerIndex++];
    }

    private void writeBooleanRaw(boolean value) {
        writeByteRaw(value ? 1 : 0);
    }

    private boolean readBooleanRaw() {
        return readByteRaw() != 0;
    }

    private void writeShortRaw(int value) {
        ensureCapacity(writerIndex + 2);
        data[writerIndex++] = (byte) (value >>> 8);
        data[writerIndex++] = (byte) value;
    }

    private int readShortRaw() {
        checkRelative(2);
        int value = ((data[readerIndex] & 0xFF) << 8) | (data[readerIndex + 1] & 0xFF);
        readerIndex += 2;
        return (short) value;
    }

    private void writeMediumRaw(int value) {
        ensureCapacity(writerIndex + 3);
        data[writerIndex++] = (byte) (value >>> 16);
        data[writerIndex++] = (byte) (value >>> 8);
        data[writerIndex++] = (byte) value;
    }

    private int readMediumRaw() {
        checkRelative(3);
        int value = ((data[readerIndex] & 0xFF) << 16)
                | ((data[readerIndex + 1] & 0xFF) << 8)
                | (data[readerIndex + 2] & 0xFF);
        readerIndex += 3;
        return (value & 0x800000) != 0 ? value | 0xFF000000 : value;
    }

    private void writeIntRaw(int value) {
        ensureCapacity(writerIndex + 4);
        data[writerIndex++] = (byte) (value >>> 24);
        data[writerIndex++] = (byte) (value >>> 16);
        data[writerIndex++] = (byte) (value >>> 8);
        data[writerIndex++] = (byte) value;
    }

    private int readIntRaw() {
        checkRelative(4);
        int value = ((data[readerIndex] & 0xFF) << 24)
                | ((data[readerIndex + 1] & 0xFF) << 16)
                | ((data[readerIndex + 2] & 0xFF) << 8)
                | (data[readerIndex + 3] & 0xFF);
        readerIndex += 4;
        return value;
    }

    private void writeLongRaw(long value) {
        ensureCapacity(writerIndex + 8);
        for (int shift = 56; shift >= 0; shift -= 8) {
            data[writerIndex++] = (byte) (value >>> shift);
        }
    }

    private long readLongRaw() {
        checkRelative(8);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (data[readerIndex + i] & 0xFFL);
        }
        readerIndex += 8;
        return value;
    }

    private void writeVarIntRaw(int value) {
        while ((value & ~0x7F) != 0) {
            writeByteRaw((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        writeByteRaw(value);
    }

    private int readVarIntRaw() {
        int value = 0;
        int position = 0;
        while (true) {
            int current = readByteRaw();
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                return value;
            }
            position += 7;
            if (position >= 35) {
                throw new IllegalStateException("VarInt too big");
            }
        }
    }

    private void writeVarLongRaw(long value) {
        while ((value & ~0x7FL) != 0) {
            writeByteRaw((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        writeByteRaw((int) value);
    }

    private long readVarLongRaw() {
        long value = 0;
        int position = 0;
        while (true) {
            int current = readByteRaw();
            value |= (long) (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                return value;
            }
            position += 7;
            if (position >= 70) {
                throw new IllegalStateException("VarLong too big");
            }
        }
    }

    private void writeUtfRaw(String text, int maxChars) {
        if (text.length() > maxChars) {
            throw new IllegalArgumentException("string too long: " + text.length()
                    + " > " + maxChars);
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxChars * 3) {
            throw new IllegalArgumentException("string too big: " + bytes.length
                    + " > " + (maxChars * 3));
        }
        writeVarIntRaw(bytes.length);
        ensureCapacity(writerIndex + bytes.length);
        System.arraycopy(bytes, 0, data, writerIndex, bytes.length);
        writerIndex += bytes.length;
    }

    private String readUtfRaw(int maxChars) {
        int length = readVarIntRaw();
        if (length < 0 || length > maxChars * 3) {
            throw new IllegalArgumentException("string too big: " + length
                    + " > " + (maxChars * 3));
        }
        checkRelative(length);
        String text = new String(data, readerIndex, length, StandardCharsets.UTF_8);
        readerIndex += length;
        if (text.length() > maxChars) {
            throw new IllegalArgumentException("string too long: " + text.length()
                    + " > " + maxChars);
        }
        return text;
    }

    // ------------------------------------------------------------------
    // Index / reset surface (G-002 §6.1: 11 index members + reset)
    // ------------------------------------------------------------------

    /**
     * The current read position.
     *
     * @zh 当前读位置。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public synchronized int getReaderIndex() {
        ensureData();
        return readerIndex;
    }

    /**
     * The current write position.
     *
     * @zh 当前写位置。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public synchronized int getWriterIndex() {
        ensureData();
        return writerIndex;
    }

    /**
     * Moves the read position (within {@code [0, writerIndex]}).
     *
     * @zh 移动读位置（须在 {@code [0, writerIndex]} 内）。
     * @zh-param index 新读位置
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public synchronized void setReaderIndex(int index) {
        ensureData();
        if (index < 0 || index > writerIndex) {
            throw new IndexOutOfBoundsException("readerIndex " + index
                    + " outside [0, " + writerIndex + "]");
        }
        readerIndex = index;
    }

    /**
     * Moves the write position (within {@code [readerIndex, capacity]}).
     *
     * @zh 移动写位置（须在 {@code [readerIndex, 容量]} 内）。
     * @zh-param index 新写位置
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public synchronized void setWriterIndex(int index) {
        ensureData();
        if (index < readerIndex || index > data.length) {
            throw new IndexOutOfBoundsException("writerIndex " + index
                    + " outside [" + readerIndex + ", " + data.length + "]");
        }
        writerIndex = index;
    }

    /**
     * Sets both positions at once.
     *
     * @zh 一次设置读写两个位置。
     * @zh-param readerIndex 新读位置
     * @zh-param writerIndex 新写位置
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public synchronized void setIndices(int readerIndex, int writerIndex) {
        ensureData();
        if (readerIndex < 0 || writerIndex < readerIndex || writerIndex > data.length) {
            throw new IndexOutOfBoundsException("indices (" + readerIndex + ", "
                    + writerIndex + ") outside capacity " + data.length);
        }
        this.readerIndex = readerIndex;
        this.writerIndex = writerIndex;
    }

    /**
     * Restores both positions to their marks (zero unless marked).
     *
     * @zh 把两个位置恢复到标记值（未标记时为 0）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public synchronized void resetIndices() {
        resetReaderIndex();
        resetWriterIndex();
    }

    /**
     * Marks the current read position.
     *
     * @zh 标记当前读位置。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public synchronized void markReaderIndex() {
        ensureData();
        markedReaderIndex = readerIndex;
    }

    /**
     * Restores the read position to its mark.
     *
     * @zh 把读位置恢复到标记值。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public synchronized void resetReaderIndex() {
        ensureData();
        readerIndex = markedReaderIndex;
    }

    /**
     * Marks the current write position.
     *
     * @zh 标记当前写位置。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public synchronized void markWriterIndex() {
        ensureData();
        markedWriterIndex = writerIndex;
    }

    /**
     * Restores the write position to its mark.
     *
     * @zh 把写位置恢复到标记值。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public synchronized void resetWriterIndex() {
        ensureData();
        writerIndex = markedWriterIndex;
    }

    /**
     * Rewinds both positions to zero; the bytes stay untouched.
     *
     * @zh 把两个位置都倒回 0；字节内容不变。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public synchronized void clear() {
        ensureData();
        readerIndex = 0;
        writerIndex = 0;
        markedReaderIndex = 0;
        markedWriterIndex = 0;
    }

    /**
     * Restores the initial bytes and positions captured when the cell
     * materialized (empty for builders).
     *
     * @zh 恢复单元物化时捕获的初始字节与初始位置（构造器为空）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public synchronized void reset() {
        ensureData();
        data = initialData.clone();
        readerIndex = initialReaderIndex;
        writerIndex = initialWriterIndex;
        markedReaderIndex = initialReaderIndex;
        markedWriterIndex = initialWriterIndex;
    }

    // ------------------------------------------------------------------
    // Scalar read/write surface (50 members)
    // ------------------------------------------------------------------

    /** Writes one boolean as a single byte. @zh 写入一个布尔值（单字节）。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeBoolean(boolean value) {
        checkWritable();
        ensureData();
        writeBooleanRaw(value);
    }

    /** Writes one boolean at an absolute index. @zh 在绝对索引处写入一个布尔值。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void setBoolean(int index, boolean value) {
        checkWritable();
        ensureData();
        checkAbsolute(index, 1);
        data[index] = (byte) (value ? 1 : 0);
    }

    /** Reads one boolean and advances the read position. @zh 读取一个布尔值并前移读位置。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized boolean readBoolean() {
        ensureData();
        return readBooleanRaw();
    }

    /** Reads one boolean at an absolute index. @zh 在绝对索引处读取一个布尔值。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized boolean getBoolean(int index) {
        ensureData();
        checkAbsolute(index, 1);
        return data[index] != 0;
    }

    /** Writes one UTF-16 code unit (2 bytes); value is the char code. @zh 写入一个 UTF-16 码元（2 字节）；值为码点。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeChar(int value) {
        checkWritable();
        ensureData();
        writeShortRaw(value);
    }

    /** Writes one char code at an absolute index. @zh 在绝对索引处写入一个字符码。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void setChar(int index, int value) {
        checkWritable();
        ensureData();
        checkAbsolute(index, 2);
        data[index] = (byte) (value >>> 8);
        data[index + 1] = (byte) value;
    }

    /** Reads one char code (0..65535). @zh 读取一个字符码（0..65535）。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized int readChar() {
        ensureData();
        return readShortRaw() & 0xFFFF;
    }

    /** Reads one char code at an absolute index. @zh 在绝对索引处读取一个字符码。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized int getChar(int index) {
        ensureData();
        checkAbsolute(index, 2);
        return (((data[index] & 0xFF) << 8) | (data[index + 1] & 0xFF)) & 0xFFFF;
    }

    /** Writes one signed byte. @zh 写入一个有符号字节。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeByte(int value) {
        checkWritable();
        ensureData();
        writeByteRaw(value);
    }

    /** Writes one byte at an absolute index. @zh 在绝对索引处写入一个字节。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void setByte(int index, int value) {
        checkWritable();
        ensureData();
        checkAbsolute(index, 1);
        data[index] = (byte) value;
    }

    /** Reads one signed byte. @zh 读取一个有符号字节。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized byte readByte() {
        ensureData();
        return (byte) readByteRaw();
    }

    /** Reads one unsigned byte (0..255). @zh 读取一个无符号字节（0..255）。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized short readUnsignedByte() {
        ensureData();
        return (short) (readByteRaw() & 0xFF);
    }

    /** Reads one signed byte at an absolute index. @zh 在绝对索引处读取一个有符号字节。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized byte getByte(int index) {
        ensureData();
        checkAbsolute(index, 1);
        return data[index];
    }

    /** Reads one unsigned byte at an absolute index. @zh 在绝对索引处读取一个无符号字节。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized short getUnsignedByte(int index) {
        ensureData();
        checkAbsolute(index, 1);
        return (short) (data[index] & 0xFF);
    }

    /** Writes one big-endian short. @zh 写入一个大端序 short。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeShort(int value) {
        checkWritable();
        ensureData();
        writeShortRaw(value);
    }

    /** Writes one short at an absolute index. @zh 在绝对索引处写入一个 short。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void setShort(int index, int value) {
        checkWritable();
        ensureData();
        checkAbsolute(index, 2);
        data[index] = (byte) (value >>> 8);
        data[index + 1] = (byte) value;
    }

    /** Reads one signed short. @zh 读取一个有符号 short。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized short readShort() {
        ensureData();
        return (short) readShortRaw();
    }

    /** Reads one unsigned short (0..65535). @zh 读取一个无符号 short（0..65535）。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized int readUnsignedShort() {
        ensureData();
        return readShortRaw() & 0xFFFF;
    }

    /** Reads one signed short at an absolute index. @zh 在绝对索引处读取一个有符号 short。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized short getShort(int index) {
        ensureData();
        checkAbsolute(index, 2);
        return (short) (((data[index] & 0xFF) << 8) | (data[index + 1] & 0xFF));
    }

    /** Reads one unsigned short at an absolute index. @zh 在绝对索引处读取一个无符号 short。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized int getUnsignedShort(int index) {
        ensureData();
        checkAbsolute(index, 2);
        return ((data[index] & 0xFF) << 8) | (data[index + 1] & 0xFF);
    }

    /** Writes one signed 3-byte medium. @zh 写入一个有符号 3 字节 medium。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeMedium(int value) {
        checkWritable();
        ensureData();
        writeMediumRaw(value);
    }

    /** Writes one medium at an absolute index. @zh 在绝对索引处写入一个 medium。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void setMedium(int index, int value) {
        checkWritable();
        ensureData();
        checkAbsolute(index, 3);
        data[index] = (byte) (value >>> 16);
        data[index + 1] = (byte) (value >>> 8);
        data[index + 2] = (byte) value;
    }

    /** Reads one signed 3-byte medium. @zh 读取一个有符号 3 字节 medium。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized int readMedium() {
        ensureData();
        return readMediumRaw();
    }

    /** Reads one unsigned 3-byte medium. @zh 读取一个无符号 3 字节 medium。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized int readUnsignedMedium() {
        ensureData();
        return readMediumRaw() & 0xFFFFFF;
    }

    /** Reads one signed medium at an absolute index. @zh 在绝对索引处读取一个有符号 medium。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized int getMedium(int index) {
        ensureData();
        checkAbsolute(index, 3);
        int value = ((data[index] & 0xFF) << 16) | ((data[index + 1] & 0xFF) << 8)
                | (data[index + 2] & 0xFF);
        return (value & 0x800000) != 0 ? value | 0xFF000000 : value;
    }

    /** Reads one unsigned medium at an absolute index. @zh 在绝对索引处读取一个无符号 medium。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized int getUnsignedMedium(int index) {
        ensureData();
        checkAbsolute(index, 3);
        return ((data[index] & 0xFF) << 16) | ((data[index + 1] & 0xFF) << 8)
                | (data[index + 2] & 0xFF);
    }

    /** Writes one big-endian int. @zh 写入一个大端序 int。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeInt(int value) {
        checkWritable();
        ensureData();
        writeIntRaw(value);
    }

    /** Writes one int at an absolute index. @zh 在绝对索引处写入一个 int。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void setInt(int index, int value) {
        checkWritable();
        ensureData();
        checkAbsolute(index, 4);
        data[index] = (byte) (value >>> 24);
        data[index + 1] = (byte) (value >>> 16);
        data[index + 2] = (byte) (value >>> 8);
        data[index + 3] = (byte) value;
    }

    /** Reads one signed int. @zh 读取一个有符号 int。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized int readInt() {
        ensureData();
        return readIntRaw();
    }

    /** Reads one unsigned int as a long. @zh 读取一个无符号 int（long 表达）。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized long readUnsignedInt() {
        ensureData();
        return readIntRaw() & 0xFFFFFFFFL;
    }

    /** Reads one signed int at an absolute index. @zh 在绝对索引处读取一个有符号 int。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized int getInt(int index) {
        ensureData();
        checkAbsolute(index, 4);
        return ((data[index] & 0xFF) << 24) | ((data[index + 1] & 0xFF) << 16)
                | ((data[index + 2] & 0xFF) << 8) | (data[index + 3] & 0xFF);
    }

    /** Reads one unsigned int at an absolute index as a long. @zh 在绝对索引处读取一个无符号 int（long 表达）。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized long getUnsignedInt(int index) {
        ensureData();
        return getInt(index) & 0xFFFFFFFFL;
    }

    /** Writes one big-endian long. @zh 写入一个大端序 long。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeLong(long value) {
        checkWritable();
        ensureData();
        writeLongRaw(value);
    }

    /** Writes one long at an absolute index. @zh 在绝对索引处写入一个 long。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void setLong(int index, long value) {
        checkWritable();
        ensureData();
        checkAbsolute(index, 8);
        for (int i = 0; i < 8; i++) {
            data[index + i] = (byte) (value >>> (56 - i * 8));
        }
    }

    /** Reads one signed long. @zh 读取一个有符号 long。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized long readLong() {
        ensureData();
        return readLongRaw();
    }

    /** Reads one long at an absolute index. @zh 在绝对索引处读取一个 long。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized long getLong(int index) {
        ensureData();
        checkAbsolute(index, 8);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (data[index + i] & 0xFFL);
        }
        return value;
    }

    /** Writes one float (4 bytes; number inputs narrow to float). @zh 写入一个 float（4 字节；数字入参收窄为 float）。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeFloat(double value) {
        checkWritable();
        ensureData();
        writeIntRaw(Float.floatToIntBits((float) value));
    }

    /** Writes one float at an absolute index. @zh 在绝对索引处写入一个 float。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void setFloat(int index, double value) {
        setInt(index, Float.floatToIntBits((float) value));
    }

    /** Reads one float. @zh 读取一个 float。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized float readFloat() {
        ensureData();
        return Float.intBitsToFloat(readIntRaw());
    }

    /** Reads one float at an absolute index. @zh 在绝对索引处读取一个 float。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized float getFloat(int index) {
        ensureData();
        return Float.intBitsToFloat(getInt(index));
    }

    /** Writes one double (8 bytes). @zh 写入一个 double（8 字节）。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeDouble(double value) {
        checkWritable();
        ensureData();
        writeLongRaw(Double.doubleToLongBits(value));
    }

    /** Writes one double at an absolute index. @zh 在绝对索引处写入一个 double。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void setDouble(int index, double value) {
        checkWritable();
        ensureData();
        checkAbsolute(index, 8);
        long bits = Double.doubleToLongBits(value);
        for (int i = 0; i < 8; i++) {
            data[index + i] = (byte) (bits >>> (56 - i * 8));
        }
    }

    /** Reads one double. @zh 读取一个 double。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized double readDouble() {
        ensureData();
        return Double.longBitsToDouble(readLongRaw());
    }

    /** Reads one double at an absolute index. @zh 在绝对索引处读取一个 double。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized double getDouble(int index) {
        ensureData();
        return Double.longBitsToDouble(getLong(index));
    }

    /**
     * Advances the write position over {@code length} zero bytes.
     *
     * @zh 跳过 {@code length} 个零字节并前移写位置。
     * @zh-param length 零字节数
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeZero(int length) {
        if (length < 0) {
            throw new IndexOutOfBoundsException("length " + length);
        }
        checkWritable();
        ensureData();
        ensureCapacity(writerIndex + length);
        java.util.Arrays.fill(data, writerIndex, writerIndex + length, (byte) 0);
        writerIndex += length;
    }

    /** Writes zero bytes at an absolute index. @zh 在绝对索引处写入零字节。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void setZero(int index, int length) {
        if (length < 0) {
            throw new IndexOutOfBoundsException("length " + length);
        }
        checkWritable();
        ensureData();
        checkAbsolute(index, length);
        java.util.Arrays.fill(data, index, index + length, (byte) 0);
    }

    /**
     * Writes raw bytes at an absolute index; accepts a byte array or any
     * array-like of numbers ({@code Uint8Array}).
     *
     * @zh 在绝对索引处写入原始字节；接受字节数组或任意数字类数组（{@code Uint8Array}）。
     * @zh-param index 目标索引
     * @zh-param bytes 字节数据
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    @ApiType("Uint8Array | Array<number>")
    public synchronized void setBytes(int index,
            @ApiType("Uint8Array | Array<number>") Object bytes) {
        byte[] raw = toBytes(bytes);
        checkWritable();
        ensureData();
        checkAbsolute(index, raw.length);
        System.arraycopy(raw, 0, data, index, raw.length);
    }

    /**
     * Reads {@code length} bytes and advances the read position; the result
     * crosses as a {@code Uint8Array}.
     *
     * @zh 读取 {@code length} 个字节并前移读位置；结果以 {@code Uint8Array} 跨界。
     * @zh-param length 字节数
     * @zh-return 字节数组
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized byte[] readBytes(int length) {
        ensureData();
        checkRelative(length);
        byte[] out = new byte[length];
        System.arraycopy(data, readerIndex, out, 0, length);
        readerIndex += length;
        return out;
    }

    /** Reads {@code length} bytes at an absolute index. @zh 在绝对索引处读取 {@code length} 个字节。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized byte[] getBytes(int index, int length) {
        ensureData();
        checkAbsolute(index, length);
        byte[] out = new byte[length];
        System.arraycopy(data, index, out, 0, length);
        return out;
    }

    /**
     * Advances the read position past {@code length} bytes without copying.
     *
     * @zh 跳过 {@code length} 个字节而不拷贝。
     * @zh-param length 字节数
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void skipBytes(int length) {
        ensureData();
        checkRelative(length);
        readerIndex += length;
    }

    // ------------------------------------------------------------------
    // Array surface (9 members)
    // ------------------------------------------------------------------

    /**
     * Writes a varint length followed by the raw bytes.
     *
     * @zh 写入 varint 长度加原始字节。
     * @zh-param bytes 字节数据
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    @ApiType("Uint8Array | Array<number>")
    public synchronized void writeByteArray(@ApiType("Uint8Array | Array<number>") Object bytes) {
        byte[] raw = toBytes(bytes);
        checkWritable();
        ensureData();
        writeVarIntRaw(raw.length);
        ensureCapacity(writerIndex + raw.length);
        System.arraycopy(raw, 0, data, writerIndex, raw.length);
        writerIndex += raw.length;
    }

    /** Reads a varint-length byte array. @zh 读取 varint 长度的字节数组。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized byte[] readByteArray() {
        ensureData();
        int length = readVarIntRaw();
        if (length < 0) {
            throw new IndexOutOfBoundsException("negative array length " + length);
        }
        return readBytes(length);
    }

    /**
     * Reads a varint-length byte array bounded by {@code maxSize}.
     *
     * @zh 读取 varint 长度且不超过 {@code maxSize} 的字节数组。
     * @zh-param maxSize 最大字节数
     * @zh-return 字节数组
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized byte[] readByteArray(int maxSize) {
        ensureData();
        int length = readVarIntRaw();
        if (length < 0 || length > maxSize) {
            throw new IllegalArgumentException("byte array size " + length
                    + " outside [0, " + maxSize + "]");
        }
        return readBytes(length);
    }

    /**
     * Writes a varint count followed by varint-encoded ints.
     *
     * @zh 写入 varint 个数加 varint 编码的 int 序列。
     * @zh-param values 整数列表
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeIntArray(List<?> values) {
        checkWritable();
        ensureData();
        writeVarIntRaw(values.size());
        for (Object value : values) {
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException("int data must be numbers");
            }
            writeVarIntRaw(number.intValue());
        }
    }

    /** Reads a varint-count int array. @zh 读取 varint 个数的 int 数组。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized List<Integer> readIntArray() {
        ensureData();
        int count = readVarIntRaw();
        if (count < 0) {
            throw new IndexOutOfBoundsException("negative array length " + count);
        }
        List<Integer> out = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            out.add(readVarIntRaw());
        }
        return out;
    }

    /**
     * Reads a varint-count int array bounded by {@code maxSize} elements.
     *
     * @zh 读取 varint 个数且不超过 {@code maxSize} 个元素的 int 数组。
     * @zh-param maxSize 最大元素数
     * @zh-return 整数列表
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized List<Integer> readIntArray(int maxSize) {
        ensureData();
        int count = readVarIntRaw();
        if (count < 0 || count > maxSize) {
            throw new IllegalArgumentException("array length " + count
                    + " outside [0, " + maxSize + "]");
        }
        List<Integer> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(readVarIntRaw());
        }
        return out;
    }

    /**
     * Writes a varint count followed by fixed 8-byte longs.
     *
     * @zh 写入 varint 个数加定长 8 字节 long 序列。
     * @zh-param values 长整型列表
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeLongArray(List<?> values) {
        checkWritable();
        ensureData();
        writeVarIntRaw(values.size());
        for (Object value : values) {
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException("long data must be numbers");
            }
            writeLongRaw(number.longValue());
        }
    }

    /** Reads a varint-count long array. @zh 读取 varint 个数的 long 数组。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized List<Long> readLongArray() {
        ensureData();
        int count = readVarIntRaw();
        if (count < 0) {
            throw new IndexOutOfBoundsException("negative array length " + count);
        }
        List<Long> out = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            out.add(readLongRaw());
        }
        return out;
    }

    /**
     * Reads a varint-count long array bounded by {@code maxSize} elements.
     *
     * @zh 读取 varint 个数且不超过 {@code maxSize} 个元素的 long 数组。
     * @zh-param maxSize 最大元素数
     * @zh-return 长整型列表
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized List<Long> readLongArray(int maxSize) {
        ensureData();
        int count = readVarIntRaw();
        if (count < 0 || count > maxSize) {
            throw new IllegalArgumentException("array length " + count
                    + " outside [0, " + maxSize + "]");
        }
        List<Long> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(readLongRaw());
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Position surface (12 members)
    // ------------------------------------------------------------------

    /**
     * Writes a block position as one packed long (vanilla encoding).
     *
     * @zh 以打包 long 写入方块坐标（原版编码）。
     * @zh-param pos 方块坐标
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeBlockPos(BlockPos pos) {
        writeBlockPos(pos.getIntX(), pos.getIntY(), pos.getIntZ());
    }

    /**
     * Writes block coordinates as one packed long.
     *
     * @zh 以打包 long 写入方块坐标分量。
     * @zh-param x X
     * @zh-param y Y
     * @zh-param z Z
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeBlockPos(int x, int y, int z) {
        checkWritable();
        ensureData();
        writeLongRaw(((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12)
                | (y & 0xFFFL));
    }

    /** Reads one packed block position. @zh 读取一个打包的方块坐标。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized BlockPos readBlockPos() {
        ensureData();
        long packed = readLongRaw();
        int x = (int) (packed >> 38);
        int z = (int) (packed << 26 >> 38);
        int y = (int) (packed << 52 >> 52);
        return new BlockPos(x, y, z);
    }

    /**
     * Writes a chunk coordinate as one packed long (vanilla encoding).
     *
     * @zh 以打包 long 写入区块坐标（原版编码）。
     * @zh-param x 区块 X
     * @zh-param z 区块 Z
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeChunkPos(int x, int z) {
        checkWritable();
        ensureData();
        writeLongRaw((x & 0xFFFFFFFFL) | ((z & 0xFFFFFFFFL) << 32));
    }

    /**
     * Writes a chunk coordinate handle as one packed long.
     *
     * @zh 以打包 long 写入区块坐标句柄。
     * @zh-param chunk 区块坐标
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeChunkPos(ChunkPos chunk) {
        writeChunkPos(chunk.getX(), chunk.getZ());
    }

    /** Reads one packed chunk coordinate. @zh 读取一个打包的区块坐标。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized ChunkPos readChunkPos() {
        ensureData();
        long packed = readLongRaw();
        return new ChunkPos((int) packed, (int) (packed >>> 32));
    }

    /**
     * Reads a chunk coordinate then looks the loaded chunk up in the world;
     * undefined when the chunk is not loaded.
     *
     * @zh 读取区块坐标后在世界中查询已加载区块；未加载时为 undefined。
     * @zh-return 区块句柄或 undefined
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized ink.tuanzi.neomacros.core.api.types.Chunk readChunk() {
        ChunkPos pos = readChunkPos();
        return ScriptApiEnvironment.adapter().world()
                .chunkAt(new ink.tuanzi.neomacros.port.model.Pos3d(
                        pos.getX() * 16 + 8, 64, pos.getZ() * 16 + 8))
                .map(data -> new ink.tuanzi.neomacros.core.api.types.Chunk(
                        data.chunkX(), data.chunkZ()))
                .orElse(null);
    }

    /**
     * Writes a chunk-section coordinate as one packed long (vanilla
     * section encoding: x 22 bits, z 22 bits, y 20 bits).
     *
     * @zh 以打包 long 写入子区块坐标（原版子区块编码：x 22 位、z 22 位、y 20 位）。
     * @zh-param chunkX 区块 X
     * @zh-param y 孺区块 Y（区块坐标系）
     * @zh-param chunkZ 区块 Z
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeChunkSectionPos(int chunkX, int y, int chunkZ) {
        checkWritable();
        ensureData();
        writeLongRaw(((chunkX & 0x3FFFFFL) << 42) | ((chunkZ & 0x3FFFFFL) << 20)
                | (y & 0xFFFFFL));
    }

    /**
     * Writes the section above the given chunk at section Y.
     *
     * @zh 写入给定区块在子区块 Y 处的子区块坐标。
     * @zh-param chunk 区块坐标
     * @zh-param y 子区块 Y（区块坐标系）
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeChunkSectionPos(ChunkPos chunk, int y) {
        writeChunkSectionPos(chunk.getX(), y, chunk.getZ());
    }

    /**
     * Reads one packed chunk-section coordinate as a {@link BlockPos}
     * carrying the section indices.
     *
     * @zh 读取一个打包的子区块坐标，以携带子区块下标的 {@link BlockPos} 返回。
     * @zh-return 子区块坐标（x/y/z 为子区块下标）
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized BlockPos readChunkSectionPos() {
        ensureData();
        long packed = readLongRaw();
        int x = (int) (packed >> 42);
        int z = (int) (packed << 22 >> 42);
        int y = (int) (packed << 44 >> 44);
        return new BlockPos(x, y, z);
    }

    /**
     * Writes a global position: dimension id then packed block position.
     *
     * @zh 写入全局坐标：维度 id 加打包方块坐标。
     * @zh-param dimension 维度标识（如 {@code minecraft:overworld}）
     * @zh-param pos 方块坐标
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeGlobalPos(String dimension, BlockPos pos) {
        writeGlobalPos(dimension, pos.getIntX(), pos.getIntY(), pos.getIntZ());
    }

    /**
     * Writes a global position from raw components.
     *
     * @zh 由原始分量写入全局坐标。
     * @zh-param dimension 维度标识
     * @zh-param x X
     * @zh-param y Y
     * @zh-param z Z
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeGlobalPos(String dimension, int x, int y, int z) {
        if (dimension == null) {
            throw new IllegalArgumentException("dimension is required");
        }
        checkWritable();
        ensureData();
        writeUtfRaw(dimension, MAX_STRING_CHARS);
        writeBlockPos(x, y, z);
    }

    // ------------------------------------------------------------------
    // Varint / uuid / string / identifier / enum surface
    // ------------------------------------------------------------------

    /** Writes one varint. @zh 写入一个 varint。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeVarInt(int i) {
        checkWritable();
        ensureData();
        writeVarIntRaw(i);
    }

    /** Reads one varint. @zh 读取一个 varint。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized int readVarInt() {
        ensureData();
        return readVarIntRaw();
    }

    /** Writes one varlong. @zh 写入一个 varlong。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeVarLong(long l) {
        checkWritable();
        ensureData();
        writeVarLongRaw(l);
    }

    /** Reads one varlong. @zh 读取一个 varlong。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized long readVarLong() {
        ensureData();
        return readVarLongRaw();
    }

    /**
     * Writes a UUID as its two 64-bit halves.
     *
     * @zh 以两个 64 位半写入 UUID。
     * @zh-param uuid UUID 字符串
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeUuid(String uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid is required");
        }
        UUID parsed = UUID.fromString(uuid);
        checkWritable();
        ensureData();
        writeLongRaw(parsed.getMostSignificantBits());
        writeLongRaw(parsed.getLeastSignificantBits());
    }

    /** Reads one UUID as its canonical string. @zh 读取一个 UUID（规范字符串）。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized String readUuid() {
        ensureData();
        long most = readLongRaw();
        long least = readLongRaw();
        return new UUID(most, least).toString();
    }

    /**
     * Writes a varint-length UTF-8 string (default cap 32767 chars).
     *
     * @zh 写入 varint 长度的 UTF-8 字符串（默认上限 32767 字符）。
     * @zh-param string 字符串
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeString(String string) {
        writeString(string, MAX_STRING_CHARS);
    }

    /**
     * Writes a varint-length UTF-8 string bounded by {@code maxLength}
     * characters.
     *
     * @zh 写入 varint 长度、不超过 {@code maxLength} 字符的 UTF-8 字符串。
     * @zh-param string 字符串
     * @zh-param maxLength 最大字符数
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeString(String string, int maxLength) {
        if (string == null) {
            throw new IllegalArgumentException("string is required");
        }
        checkWritable();
        ensureData();
        writeUtfRaw(string, maxLength);
    }

    /** Reads a varint-length UTF-8 string. @zh 读取 varint 长度的 UTF-8 字符串。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized String readString() {
        ensureData();
        return readUtfRaw(MAX_STRING_CHARS);
    }

    /**
     * Reads a varint-length UTF-8 string bounded by {@code maxLength}
     * characters.
     *
     * @zh 读取 varint 长度、不超过 {@code maxLength} 字符的 UTF-8 字符串。
     * @zh-param maxLength 最大字符数
     * @zh-return 字符串
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized String readString(int maxLength) {
        ensureData();
        return readUtfRaw(maxLength);
    }

    /**
     * Writes an identifier as its string form.
     *
     * @zh 以字符串形式写入标识符。
     * @zh-param id 标识符（如 {@code minecraft:stone}）
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeIdentifier(String id) {
        writeString(id);
    }

    /** Reads an identifier string. @zh 读取标识符字符串。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized String readIdentifier() {
        return readString();
    }

    /**
     * Writes an enum constant by ordinal (vanilla enum encoding); the
     * argument must be a host enum, usually reached through the {@code java}
     * surface.
     *
     * @zh 按序数写入枚举常量（原版枚举编码）；实参须为宿主枚举，通常经 {@code java} 面获得。
     * @zh-param constant 枚举常量
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeEnumConstant(Object constant) {
        if (!(constant instanceof Enum<?> value)) {
            throw new IllegalArgumentException("expected an enum constant, got: "
                    + constant);
        }
        checkWritable();
        ensureData();
        writeVarIntRaw(value.ordinal());
    }

    /**
     * Reads an enum constant by ordinal from the named enum class and
     * returns its constant name.
     *
     * @zh 按序数从指定枚举类读取枚举常量并返回常量名。
     * @zh-param name 枚举类二进制名（如 {@code net.minecraft.core.Direction}）
     * @zh-return 常量名
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized String readEnum(String name) {
        if (name == null) {
            throw new IllegalArgumentException("enum class name is required");
        }
        ensureData();
        Class<?> type = port().resolveClass(name);
        if (type == null || !type.isEnum()) {
            throw new IllegalArgumentException("not an enum class: " + name);
        }
        Object[] constants = type.getEnumConstants();
        int ordinal = readVarIntRaw();
        if (ordinal < 0 || ordinal >= constants.length) {
            throw new IllegalArgumentException("enum ordinal " + ordinal
                    + " outside " + constants.length + " constants of " + name);
        }
        return ((Enum<?>) constants[ordinal]).name();
    }

    // ------------------------------------------------------------------
    // Time / hit-result surface (B members; Date/PublicKey/BitSet are D)
    // ------------------------------------------------------------------

    /**
     * Writes an instant as epoch milliseconds (vanilla {@code writeInstant}
     * encoding).
     *
     * @zh 以 epoch 毫秒写入时刻（原版 {@code writeInstant} 编码）。
     * @zh-param epochMillis epoch 毫秒数
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeInstant(long epochMillis) {
        checkWritable();
        ensureData();
        writeLongRaw(epochMillis);
    }

    /** Reads an instant as epoch milliseconds. @zh 读取时刻（epoch 毫秒数）。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized long readInstant() {
        ensureData();
        return readLongRaw();
    }

    /**
     * Writes a block hit result: packed block position, direction ordinal,
     * the hit location as three floats relative to the block origin, then
     * the inside-block and world-border flags.
     *
     * @zh 写入方块命中结果：打包方块坐标、方向序数、相对方块原点的三 float 命中位置，以及方块内与边界命中标记。
     * @zh-param hit 命中结果对象 {@code {pos, direction, offsetX, offsetY, offsetZ, insideBlock, worldBorder}}
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeBlockHitResult(java.util.Map<String, Object> hit) {
        if (hit == null) {
            throw new IllegalArgumentException("hit result is required");
        }
        BlockPos pos = hitPos(hit);
        String direction = String.valueOf(hit.get("direction"));
        int ordinal = directionOrdinal(direction);
        float offsetX = number(hit.get("offsetX"));
        float offsetY = number(hit.get("offsetY"));
        float offsetZ = number(hit.get("offsetZ"));
        boolean insideBlock = Boolean.TRUE.equals(hit.get("insideBlock"));
        boolean worldBorder = Boolean.TRUE.equals(hit.get("worldBorder"));
        writeBlockHit(pos, ordinal, offsetX, offsetY, offsetZ, insideBlock, worldBorder);
    }

    /**
     * Resolves the targeted block position from either a nested
     * {@code pos} (BlockPos handle or plain {@code {x, y, z}} data) or
     * flat {@code x}/{@code y}/{@code z} keys: handles nested in object
     * literals cross as neutral data on the way in.
     *
     * @zh 从嵌套 {@code pos}（BlockPos 句柄或普通 {@code {x, y, z}} 数据）或扁平 {@code x}/{@code y}/{@code z} 键解析目标方块坐标：字面量中嵌套的句柄进入宿主时会转成中立数据。
     */
    private static BlockPos hitPos(java.util.Map<String, Object> hit) {
        Object nested = hit.get("pos");
        if (nested instanceof BlockPos pos) {
            return pos;
        }
        if (nested instanceof java.util.Map<?, ?> map) {
            return new BlockPos(intOf(map.get("x")), intOf(map.get("y")),
                    intOf(map.get("z")));
        }
        if (hit.containsKey("x") || hit.containsKey("y") || hit.containsKey("z")) {
            return new BlockPos(intOf(hit.get("x")), intOf(hit.get("y")),
                    intOf(hit.get("z")));
        }
        throw new IllegalArgumentException(
                "hit result requires pos {x, y, z} (BlockPos or plain data)");
    }

    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * Writes a block hit result from raw components; {@code pos} is the
     * exact hit location and {@code blockPos} the targeted block.
     *
     * @zh 由原始分量写入方块命中结果；{@code pos} 为精确命中位置，{@code blockPos} 为目标方块。
     * @zh-param pos 精确命中位置
     * @zh-param direction 方向名（如 {@code north}）
     * @zh-param blockPos 目标方块坐标
     * @zh-param missed 是否未命中（线格式无此位，写入为普通命中）
     * @zh-param insideBlock 是否在方块内
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeBlockHitResult(Pos3D pos, String direction,
            BlockPos blockPos, boolean missed, boolean insideBlock) {
        if (pos == null || blockPos == null || direction == null) {
            throw new IllegalArgumentException("pos, direction and blockPos are required");
        }
        int ordinal = directionOrdinal(direction);
        float offsetX = (float) (pos.getX() - blockPos.getIntX());
        float offsetY = (float) (pos.getY() - blockPos.getIntY());
        float offsetZ = (float) (pos.getZ() - blockPos.getIntZ());
        // The vanilla-style wire format carries no missed bit: a missed hit
        // round-trips as an ordinary hit at the same location (G-002 ⚠).
        writeBlockHit(blockPos, ordinal, offsetX, offsetY, offsetZ,
                insideBlock, false);
    }

    /**
     * Reads one block hit result as a plain object.
     *
     * @zh 读取一个方块命中结果（普通对象）。
     * @zh-return {@code {pos, direction, offsetX, offsetY, offsetZ, insideBlock, worldBorder}}
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized java.util.Map<String, Object> readBlockHitResult() {
        ensureData();
        BlockPos pos = readBlockPos();
        int ordinal = readVarIntRaw();
        float offsetX = Float.intBitsToFloat(readIntRaw());
        float offsetY = Float.intBitsToFloat(readIntRaw());
        float offsetZ = Float.intBitsToFloat(readIntRaw());
        boolean insideBlock = readBooleanRaw();
        boolean worldBorder = readBooleanRaw();
        String direction = ordinal >= 0 && ordinal < DIRECTIONS.length
                ? DIRECTIONS[ordinal] : String.valueOf(ordinal);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("pos", pos);
        out.put("direction", direction);
        out.put("offsetX", (double) offsetX);
        out.put("offsetY", (double) offsetY);
        out.put("offsetZ", (double) offsetZ);
        out.put("insideBlock", insideBlock);
        out.put("worldBorder", worldBorder);
        return out;
    }

    private void writeBlockHit(BlockPos pos, int ordinal, float offsetX,
            float offsetY, float offsetZ, boolean insideBlock, boolean worldBorder) {
        checkWritable();
        ensureData();
        writeBlockPos(pos);
        writeVarIntRaw(ordinal);
        writeIntRaw(Float.floatToIntBits(offsetX));
        writeIntRaw(Float.floatToIntBits(offsetY));
        writeIntRaw(Float.floatToIntBits(offsetZ));
        writeBooleanRaw(insideBlock);
        writeBooleanRaw(worldBorder);
    }

    private static int directionOrdinal(String direction) {
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (DIRECTIONS[i].equalsIgnoreCase(direction)) {
                return i;
            }
        }
        throw new IllegalArgumentException("unknown direction: " + direction
                + " (expected down/up/north/south/west/east)");
    }

    private static float number(Object value) {
        return value instanceof Number number ? number.floatValue() : 0f;
    }

    // ------------------------------------------------------------------
    // Collection / map / optional / registry-key surface
    // ------------------------------------------------------------------

    /**
     * Writes a varint registry-key id string (vanilla resource-key
     * encoding: the identifier only).
     *
     * @zh 写入 varint 注册表键 id 字符串（原版资源键编码：仅标识符）。
     * @zh-param id 键 id（如 {@code minecraft:overworld}）
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeRegistryKey(String id) {
        writeString(id);
    }

    /**
     * Reads a registry-key id string; the registry argument documents which
     * registry the key belongs to (parity with the upstream signature) and
     * does not change the read.
     *
     * @zh 读取注册表键 id 字符串；registry 参数标注键所属注册表（与上游签名对齐），不影响读取。
     * @zh-param registry 注册表键（如 {@code minecraft:dimension_type}）
     * @zh-return 键 id 字符串
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized String readRegistryKey(String registry) {
        return readString();
    }

    /**
     * Writes a varint count then each element through {@code writer}.
     *
     * @zh 写入 varint 个数，随后逐元素经 {@code writer} 写入。
     * @zh-param collection 元素列表
     * @zh-param writer 逐元素写入回调（首参为本缓冲区）
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized <T> void writeCollection(List<T> collection,
            BiConsumer<PacketBuffer, T> writer) {
        if (collection == null || writer == null) {
            throw new IllegalArgumentException("collection and writer are required");
        }
        checkWritable();
        ensureData();
        writeVarIntRaw(collection.size());
        for (T item : collection) {
            writer.accept(this, item);
        }
    }

    /**
     * Reads a varint count then each element through {@code reader}.
     *
     * @zh 读取 varint 个数，随后逐元素经 {@code reader} 读取。
     * @zh-param reader 逐元素读取回调（首参为本缓冲区）
     * @zh-return 元素列表
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized <T> List<T> readList(Function<PacketBuffer, T> reader) {
        if (reader == null) {
            throw new IllegalArgumentException("reader is required");
        }
        ensureData();
        int count = readVarIntRaw();
        if (count < 0) {
            throw new IndexOutOfBoundsException("negative list length " + count);
        }
        List<T> out = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            out.add(reader.apply(this));
        }
        return out;
    }

    /**
     * Writes a varint count then each int as a varint.
     *
     * @zh 写入 varint 个数，随后逐个以 varint 写入整数。
     * @zh-param list 整数列表
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized void writeIntList(List<?> list) {
        if (list == null) {
            throw new IllegalArgumentException("list is required");
        }
        checkWritable();
        ensureData();
        writeVarIntRaw(list.size());
        for (Object value : list) {
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException("int data must be numbers");
            }
            writeVarIntRaw(number.intValue());
        }
    }

    /** Reads a varint-count int list. @zh 读取 varint 个数的整数列表。 */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized List<Integer> readIntList() {
        return readIntArray();
    }

    /**
     * Writes a varint count then each entry as key followed by value.
     *
     * @zh 写入 varint 个数，随后逐项写入键与值。
     * @zh-param map 键值对象
     * @zh-param keyWriter 键写入回调
     * @zh-param valueWriter 值写入回调
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized <K, V> void writeMap(java.util.Map<K, V> map,
            BiConsumer<PacketBuffer, K> keyWriter,
            BiConsumer<PacketBuffer, V> valueWriter) {
        if (map == null || keyWriter == null || valueWriter == null) {
            throw new IllegalArgumentException("map and writers are required");
        }
        checkWritable();
        ensureData();
        writeVarIntRaw(map.size());
        for (java.util.Map.Entry<K, V> entry : map.entrySet()) {
            keyWriter.accept(this, entry.getKey());
            valueWriter.accept(this, entry.getValue());
        }
    }

    /**
     * Reads a varint count then each entry as key followed by value,
     * returned as {@code [[key, value], ...]} pairs so non-string keys
     * survive the crossing.
     *
     * @zh 读取 varint 个数，随后逐项读取键与值，以 {@code [[键, 值], ...]} 对数组返回，保住非字符串键。
     * @zh-param keyReader 键读取回调
     * @zh-param valueReader 值读取回调
     * @zh-return 键值对数组
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized <K, V> List<List<Object>> readMap(
            Function<PacketBuffer, K> keyReader, Function<PacketBuffer, V> valueReader) {
        if (keyReader == null || valueReader == null) {
            throw new IllegalArgumentException("readers are required");
        }
        ensureData();
        int count = readVarIntRaw();
        if (count < 0) {
            throw new IndexOutOfBoundsException("negative map length " + count);
        }
        List<List<Object>> out = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            K key = keyReader.apply(this);
            V value = valueReader.apply(this);
            List<Object> pair = new ArrayList<>(2);
            pair.add(key);
            pair.add(value);
            out.add(pair);
        }
        return out;
    }

    /**
     * Reads a varint count then applies {@code reader} to each element,
     * collecting the results.
     *
     * @zh 读取 varint 个数并对每个元素应用 {@code reader}，收集结果。
     * @zh-param reader 逐元素读取回调
     * @zh-return 元素列表
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized <T> List<T> readAll(Function<PacketBuffer, T> reader) {
        return readList(reader);
    }

    /**
     * Writes {@code true} plus the value when present, {@code false}
     * otherwise ({@code undefined} reads back as absent).
     *
     * @zh 有值写 {@code true} 加值，否则写 {@code false}（{@code undefined} 回读为缺席）。
     * @zh-param value 值（可省）
     * @zh-param writer 有值时的写入回调
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized <T> void writeOptional(T value,
            BiConsumer<PacketBuffer, T> writer) {
        if (writer == null) {
            throw new IllegalArgumentException("writer is required");
        }
        checkWritable();
        ensureData();
        if (value == null) {
            writeBooleanRaw(false);
        } else {
            writeBooleanRaw(true);
            writer.accept(this, value);
        }
    }

    /**
     * Reads an optional value: {@code undefined} when absent.
     *
     * @zh 读取可选值：缺席时为 undefined。
     * @zh-param reader 有值时的读取回调
     * @zh-return 值或 undefined
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    public synchronized <T> T readOptional(Function<PacketBuffer, T> reader) {
        if (reader == null) {
            throw new IllegalArgumentException("reader is required");
        }
        ensureData();
        return readBooleanRaw() ? reader.apply(this) : null;
    }

    // ------------------------------------------------------------------
    // Decode / send surface (toPacket + send; Class overloads are D)
    // ------------------------------------------------------------------

    /**
     * Decodes the readable bytes as a packet: the source packet's class
     * when no name is given, otherwise the named packet of the source (or
     * {@code play}) protocol. Returns a raw {@code JavaObject}.
     *
     * @zh 把可读字节解码为包：未给名称时用来源包类，否则用来源（或 {@code play}）协议下的命名包。返回原始 {@code JavaObject}。
     * @zh-param name 包 id（如 {@code minecraft:swing}），省略时用来源包
     * @zh-param protocol 协议阶段（{@code handshake}/{@code status}/{@code login}/{@code configuration}/{@code play}），省略时用来源阶段
     * @zh-return 原始包对象
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    @ApiPermission("java")
    @ApiType("JavaObject")
    public synchronized Object toPacket(@ApiOptional String name,
            @ApiOptional String protocol) {
        byte[] payload = payloadBytes();
        String id = name != null ? name : sourceId;
        if (id == null) {
            throw new IllegalStateException(
                    "packet buffer has no source packet; pass a packet id");
        }
        String phase = protocol != null ? protocol
                : sourceProtocol != null ? sourceProtocol : "play";
        String flow = name != null ? null : sourceFlow;
        return port().decodePacket(id, phase, flow, payload, 0);
    }

    /**
     * Decodes the readable bytes and sends the packet on the client's
     * connection; the send still passes through {@code packet:send}.
     * Requires the {@code packet} capability.
     *
     * @zh 把可读字节解码并在客户端连接上发送；发送仍会经过 {@code packet:send}。需 {@code packet} 能力。
     * @zh-param name 包 id，省略时用来源包
     * @zh-return 发送完成的 Promise
     */
    @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*")
    @ApiPermission("packet")
    @ApiType("Promise<void>")
    public java.util.concurrent.CompletableFuture<Void> send(
            @ApiOptional String name) {
        Object packet = toPacket(name, null);
        return port().sendRawPacket(packet);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Coerces an array-like payload into raw bytes (FsApi convention). */
    private static byte[] toBytes(Object data) {
        if (data instanceof byte[] raw) {
            return raw;
        }
        if (data instanceof List<?> numbers) {
            byte[] out = new byte[numbers.size()];
            for (int i = 0; i < out.length; i++) {
                Object entry = numbers.get(i);
                if (!(entry instanceof Number number)) {
                    throw new IllegalArgumentException("byte data must be numbers");
                }
                out[i] = number.byteValue();
            }
            return out;
        }
        if (data instanceof String text) {
            return text.getBytes(StandardCharsets.ISO_8859_1);
        }
        throw new IllegalArgumentException("byte data must be an array of numbers");
    }
}
