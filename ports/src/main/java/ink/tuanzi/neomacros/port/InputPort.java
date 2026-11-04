package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.KeyPressEdge;

import java.util.function.Consumer;
import java.util.function.Function;

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

    /**
     * Injects one raw key state (down/up) as if the user pressed it.
     * {@code keyName} uses the vanilla translation-key form.
     */
    default void injectKey(String keyName, boolean down) {
        throw new UnsupportedOperationException("input injection");
    }

    /**
     * Injects one keybind state (down/up) by bind name ({@code key.use} and
     * friends), as if the user pressed the bound input.
     */
    default void injectBind(String bindName, boolean down) {
        throw new UnsupportedOperationException("input injection");
    }

    /**
     * Registers the cancellable key-press event dispatch sink (the event
     * wiring). The sink returns true when a sync subscriber canceled the
     * edge.
     */
    default void onKeyPress(java.util.function.Function<KeyPressEdge, Boolean> sink) {
    }

    /**
     * Fires one raw keyboard edge into the event sink; returns the cancel
     * verdict (true = a sync subscriber canceled the key).
     */
    default boolean fireKeyPress(KeyPressEdge edge) {
        return false;
    }
}
