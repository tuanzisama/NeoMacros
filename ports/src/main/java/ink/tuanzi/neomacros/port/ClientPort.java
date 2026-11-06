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
 * window/input surfaces, connection lifecycle, and read-only registry and
 * option slices.
 */
public interface ClientPort {

    /** Returns the current frames-per-second measurement of the client. */
    int fps();

    /** Returns the game version string the client runs on. */
    String gameVersion();

    /**
     * Returns the raw host client instance for the escape layer; only
     * reachable through the facade member gated by the {@code java}
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

    /** Returns a small read-only slice of the game options. */
    default Optional<OptionsData> options() {
        return Optional.empty();
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
