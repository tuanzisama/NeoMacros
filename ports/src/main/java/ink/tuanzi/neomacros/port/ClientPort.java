package ink.tuanzi.neomacros.port;

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
