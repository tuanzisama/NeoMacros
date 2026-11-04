package ink.tuanzi.neomacros.port;

/**
 * Client context port: process-level facts about the running game client
 * (frame counter, version) and the raw client instance for the escape layer.
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
}
