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
     */
    void registerKeybind(String keyName, Consumer<Integer> sink);

    /**
     * Registers a sink for raw key presses of one key ({@code keyName} as
     * above). Fires on press edge only.
     */
    void registerKeyPress(String keyName, Runnable sink);

    /** Registers a sink for mouse wheel scroll events (positive = up). */
    void onScroll(Consumer<Double> sink);
}
