package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.fabric.web.v1.MacroView;
import ink.tuanzi.neomacros.fabric.web.v1.Meta;
import ink.tuanzi.neomacros.fabric.web.v1.StateSnapshot;

/**
 * The mod-side capabilities the web panel exposes, implemented by the
 * fabric layer over the module manager and config. Game-state methods run
 * on the game main thread (the controller dispatches them there and maps a
 * timeout to 504); everything else is plain thread-safe state.
 */
public interface PanelBackend {

    /** The version-negotiation probe payload. */
    Meta meta();

    /**
     * The full state snapshot. Must be called on the game main thread; may
     * reject actions with an {@code ApiError}.
     */
    StateSnapshot snapshot();

    /**
     * Runs one macro lifecycle action ({@code enable/disable/start/stop/
     * reload}). Must be called on the game main thread.
     */
    MacroView macroAction(String id, String action);
}
