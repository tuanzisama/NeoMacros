package ink.tuanzi.neomacros.port;

import java.util.function.Consumer;

/**
 * Input sources: keybind registration (surfaced in the vanilla controls
 * menu), raw key press/release streams, and the mouse wheel.
 *
 * <p>Sinks run on the game main thread in registration order.</p>
 */
public interface InputPort {

    /**
     * Registers a persistent keybind. {@code keyName} uses the vanilla
     * translation-key form ({@code key.keyboard.k}); the binding appears in
     * the controls menu and fires while the game dispatches input, on the
     * main thread.
     *
     * @param keyName vanilla key translation key
     * @param sink invoked on every press (click count semantics)
     * @return an opaque handle for {@link #unregister(Object)}
     */
    Object registerKeybind(String keyName, Consumer<Integer> sink);

    /**
     * Registers a sink for raw key presses of one key ({@code keyName} as
     * above). Fires on press edge only.
     */
    void registerKeyPress(String keyName, Runnable sink);

    /** Removes a previously registered keybind or press listener. */
    void unregister(Object handle);

    /**
     * Registers one manifest-driven script binding keyed by script name;
     * rebinding the same script replaces its previous binding.
     */
    void registerScriptBinding(String scriptName, String keyName, Consumer<Integer> sink);

    /** Removes one script's manifest-driven binding, if present. */
    void unregisterScriptBinding(String scriptName);

    /** Registers a sink for mouse wheel scroll events (positive = up). */
    void onScroll(Consumer<Double> sink);
}
