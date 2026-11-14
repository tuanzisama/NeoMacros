package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.BlockStateData;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.ModData;
import ink.tuanzi.neomacros.port.model.OptionsData;
import ink.tuanzi.neomacros.port.model.Pos2d;
import ink.tuanzi.neomacros.port.model.ServerInfoData;
import ink.tuanzi.neomacros.port.model.WindowData;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Client context port: process-level facts about the running game client
 * (frame counter, version), the raw client instance for the escape layer,
 * window/input surfaces, connection lifecycle, read-only registry queries,
 * and the game-options face with its write actions.
 */
public interface ClientPort {

    /** Returns the current frames-per-second measurement of the client. */
    int fps();

    /** Returns the game version string the client runs on. */
    String gameVersion();

    /**
     * Returns the raw host client instance for the escape layer; only
     * reachable through the API member gated by the {@code java}
     * permission.
     */
    Object minecraftInstance();

    /** Connects to the server at the address and resolves once initiated. */
    default CompletableFuture<Void> connect(String address) {
        return unsupportedFuture("connect");
    }

    /** Disconnects from the current server and resolves once initiated. */
    default CompletableFuture<Void> disconnect() {
        return unsupportedFuture("disconnect");
    }

    /** Loads the local save with the given name and resolves once initiated. */
    default CompletableFuture<Void> loadWorld(String name) {
        return unsupportedFuture("loadWorld");
    }

    /** Closes the game gracefully. */
    default void shutdown() {
    }

    /** Closes the game immediately, skipping save and shutdown hooks. */
    default void forceShutdown() {
    }

    /** Returns the server-reported latency for the local player, or empty. */
    default Optional<Integer> ping() {
        return Optional.empty();
    }

    /** Returns the installed mods in loader order. */
    default List<ModData> mods() {
        return List.of();
    }

    /** Returns whether a mod with the identifier is loaded. */
    default boolean isModLoaded(String id) {
        return false;
    }

    /** Returns whether the game runs in a development environment. */
    default boolean devEnvironment() {
        return false;
    }

    /** Grabs the mouse cursor back into the game window. */
    default void grabMouse() {
    }

    /** Releases the mouse cursor to the operating system. */
    default void releaseMouse() {
    }

    /** Returns the current system clipboard text. */
    default String clipboard() {
        return "";
    }

    /** Writes text to the system clipboard. */
    default void setClipboard(String value) {
    }

    /** Returns the window metrics, or empty before the window exists. */
    default Optional<WindowData> window() {
        return Optional.empty();
    }

    /** Returns the cursor position in window pixels. */
    default Pos2d mouse() {
        return new Pos2d(0, 0);
    }

    /** Returns the connected server entry, or empty in singleplayer. */
    default Optional<ServerInfoData> serverInfo() {
        return Optional.empty();
    }

    /** Returns the full game-options face, or empty when it is unavailable. */
    default Optional<OptionsData> options() {
        return Optional.empty();
    }

    /**
     * Returns the vanilla key-mapping catalog (bind names, category keys and
     * binds grouped by category), or empty when the options face is
     * unavailable.
     */
    default Optional<ink.tuanzi.neomacros.port.model.KeybindCatalogData> keybindCatalog() {
        return Optional.empty();
    }

    /**
     * Returns one game option value by its option name (Boolean, Double, or
     * String), or empty when the name is unknown on this node.
     */
    default Optional<Object> optionValue(String name) {
        return Optional.empty();
    }

    /**
     * Returns the available audio-device identifiers, starting with the empty
     * identifier that selects the system default device.
     */
    default List<String> audioDevices() {
        return List.of();
    }

    /**
     * Writes one option-backed value by its option name. The write settles
     * once the client has stored the value; invalid or out-of-range values
     * reject. Read-back verification stays with the caller.
     */
    default CompletableFuture<Void> setOption(String name, Object value) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("client port cannot set " + name));
    }

    /** Persists the current option face to disk. */
    default CompletableFuture<Void> saveOptions() {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("client port cannot save options"));
    }

    /**
     * Selects the resource packs to enable and persists the selection;
     * required packs stay selected regardless of the requested list.
     */
    default CompletableFuture<Void> setEnabledResourcePacks(List<String> enabled) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("client port cannot select resource packs"));
    }

    /**
     * Enables or disables server-provided resource packs; the future settles
     * once the new policy is stored.
     */
    default CompletableFuture<Void> setServerResourcePacksDisabled(boolean disabled) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("client port cannot policy server packs"));
    }

    /**
     * Selects the game language when its code is known and reloads resources;
     * unknown codes reject.
     */
    default CompletableFuture<Void> setLanguage(String languageCode) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("client port cannot set language"));
    }

    /**
     * Sends a difficulty-lock request to the server. The future settles once
     * the request is sent; the stored state flips when the server applies it.
     */
    default CompletableFuture<Void> setDifficultyLocked(boolean locked) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("client port cannot lock difficulty"));
    }

    /** Sets the camera mode ordinal (0 first person, 1 back, 2 front). */
    default CompletableFuture<Void> setCameraMode(int mode) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("client port cannot set camera mode"));
    }

    /** Enables or disables the smooth-camera toggle. */
    default CompletableFuture<Void> setSmoothCamera(boolean enabled) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("client port cannot set smooth camera"));
    }

    /**
     * Resizes the window in screen pixels; rejects while fullscreen or for
     * non-positive sizes.
     */
    default CompletableFuture<Void> setWindowSize(int width, int height) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("client port cannot resize the window"));
    }

    /** Broadcasts the current option face to the server. */
    default void sendSyncedOptions() {
    }

    /**
     * Captures the main framebuffer as PNG-encoded image data, resolving on
     * the render thread after the next frame read.
     */
    default CompletableFuture<ink.tuanzi.neomacros.port.model.ImageData> screenshot() {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("client port cannot screenshot"));
    }

    /**
     * Returns the identifiers of one registry kind, e.g. {@code block} or
     * {@code item}; unknown kinds throw.
     */
    default List<String> registryIds(String kind) {
        throw unsupportedOp("registryIds");
    }

    /**
     * Returns the full identifier when the registry kind contains the id
     * (missing namespace defaults to {@code minecraft}), or empty.
     */
    default Optional<String> registryLookup(String kind, String id) {
        return Optional.empty();
    }

    /**
     * Returns one registered block state: the default state of the block
     * when {@code props} is null or blank, otherwise the state parsed from
     * vanilla command property syntax (e.g. {@code [facing=north]}).
     * Unknown identifiers answer empty; malformed property syntax rejects
     * with {@link IllegalArgumentException}.
     */
    default Optional<BlockStateData> blockState(String id, String props) {
        return Optional.empty();
    }

    /**
     * Returns the default state of every registered block, sorted by
     * identifier.
     */
    default List<BlockStateData> blockStates() {
        return List.of();
    }

    /**
     * Returns one item stack: the default instance when {@code components}
     * is null or blank, otherwise the stack parsed from vanilla item
     * command component syntax (e.g. {@code [damage=12]}). Unknown
     * identifiers answer empty; malformed component syntax rejects with
     * {@link IllegalArgumentException}; component parsing needs a
     * connection and answers empty without one.
     */
    default Optional<ItemStackData> itemStack(String id, String components) {
        return Optional.empty();
    }

    /**
     * Returns the default stack of every registered item, sorted by
     * identifier.
     */
    default List<ItemStackData> defaultItemStacks() {
        return List.of();
    }

    /**
     * Returns the identifiers of the connected server's enchantment
     * registry (data-driven, not part of the built-in registries); empty
     * without a connection.
     */
    default List<String> enchantmentIds() {
        return List.of();
    }

    /**
     * Returns the identifiers of the connected server's painting variant
     * registry (data-driven); empty without a connection.
     */
    default List<String> paintingIds() {
        return List.of();
    }

    /**
     * Returns the identifiers of the world-generation feature registry;
     * the registry name moved between nodes ({@code worldgen/feature}
     * before 26.3, {@code worldgen/feature_type} from 26.3 on).
     */
    default List<String> featureIds() {
        return List.of();
    }

    /**
     * Registers an incoming-packet observer. Sinks run on the game main
     * thread after the adapter's suppression list and the hub's fast path
     * have already accepted the packet.
     *
     * @zh 注册下行包观测器；抑制名单与快路径放行后，回调在游戏主线程执行。
     */
    default void onPacketReceive(java.util.function.Consumer<
            ink.tuanzi.neomacros.port.model.PacketRef> sink) {
    }

    /**
     * Registers the outgoing-packet interceptor. Sinks run inline on the
     * game main thread so the verdict can cancel or replace the packet
     * before it reaches the wire.
     *
     * @zh 注册上行包拦截器；回调在游戏主线程内联执行，裁决可在上线前取消或替换该包。
     */
    default void onPacketSend(java.util.function.Function<
            ink.tuanzi.neomacros.port.model.PacketRef,
            ink.tuanzi.neomacros.port.model.PacketSendVerdict> sink) {
    }

    /**
     * Returns whether the packet has a standalone codec for the given
     * protocol phase; packets without one (bundles) expose no buffer.
     *
     * @zh 该包在指定协议阶段是否有独立编解码器；无编解码器的包（如 bundle）不提供缓冲区。
     */
    default boolean canEncodePacket(Object packet, String protocol) {
        return false;
    }

    /**
     * Encodes the packet into canonical bytes for the given protocol phase.
     * Rejects when the packet has no standalone codec.
     *
     * @zh 按指定协议阶段把包编码为规范字节；包无独立编解码器时 reject。
     */
    default byte[] encodePacket(Object packet, String protocol) {
        throw unsupportedOp("encodePacket");
    }

    /**
     * Decodes {@code bytes} from {@code offset} to the end as the named
     * packet of the given protocol phase and flow ({@code serverbound} /
     * {@code clientbound}); a null flow tries serverbound first, then
     * clientbound, because builder sends craft outgoing packets. Rejects on
     * unknown ids, unknown phases or malformed bytes.
     *
     * @zh 把 {@code bytes} 从 {@code offset} 到末尾按指定协议阶段与流向（{@code serverbound}/{@code clientbound}）解码为命名包；流向为 null 时先试 serverbound 再试 clientbound（构造器发送面向出站包）。id/阶段未知或字节非法时 reject。
     */
    default Object decodePacket(String packetId, String protocol, String flow,
            byte[] bytes, int offset) {
        throw unsupportedOp("decodePacket");
    }

    /**
     * Resolves a script-supplied binary class name to the runtime class
     * (mapping-aware on obfuscated nodes); null when unresolvable.
     *
     * @zh 把脚本给出的二进制类名解析为运行时类（混淆节点上经映射感知）；无法解析时为 null。
     */
    default Class<?> resolveClass(String name) {
        try {
            return Class.forName(name, false, ClientPort.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Sends one raw packet object on the client's connection. The future
     * settles once the send has been handed to the connection.
     *
     * @zh 在客户端连接上发送一个原始包对象；移交给连接后 future settle。
     */
    default java.util.concurrent.CompletableFuture<Void> sendRawPacket(Object packet) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("client port cannot send packets"));
    }

    /**
     * Returns the namespaced ids of every packet the client can encode,
     * sorted; empty when the packet face is unavailable.
     *
     * @zh 返回客户端可编码的全部包 id（namespaced、已排序）；包面不可用时为空。
     */
    default List<String> packetIds() {
        return List.of();
    }

    /** A rejected future naming the unsupported operation. */
    private static CompletableFuture<Void> unsupportedFuture(String op) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("client port cannot " + op));
    }

    /** An exception naming the unsupported operation. */
    private static UnsupportedOperationException unsupportedOp(String op) {
        return new UnsupportedOperationException("client port cannot " + op);
    }
}
