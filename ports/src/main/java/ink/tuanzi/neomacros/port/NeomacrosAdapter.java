package ink.tuanzi.neomacros.port;

/**
 * Aggregate factory handing core the port implementations of one milestone
 * node. The fabric entrypoint constructs the adapter implementation directly
 * (single-loader benefit: no ServiceLoader probing) and passes it to core's
 * bootstrap, which pulls the ports it needs. Factory methods may create port
 * implementations lazily. Growing the port set appends one factory method per
 * new port; existing signatures never change.
 */
public interface NeomacrosAdapter {

    /** Returns the game lifecycle port (startup/quit, world sessions, ticks). */
    LifecyclePort lifecycle();

    /** Returns the chat port (output services and the receive source). */
    ChatPort chat();

    /** Returns the world data port. */
    WorldPort world();

    /** Returns the entity query port. */
    EntityPort entity();

    /** Returns the local player port. */
    PlayerPort player();

    /** Returns the input port (keybinds, raw keys, scroll). */
    InputPort input();

    /** Returns the inventory query port. */
    InventoryPort inventory();
}
