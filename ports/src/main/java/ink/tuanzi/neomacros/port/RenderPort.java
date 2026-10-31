package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.DrawContext2d;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * HUD overlay rendering: z-ordered layers drawn on top of the vanilla HUD.
 *
 * <p>Layers register with a stable identifier for replay and z-order
 * management. The render callback fires every frame on the game main
 * thread.</p>
 */
public interface RenderPort {

    /**
     * Registers a HUD layer at the given z-order. Layers render after the
     * vanilla HUD; higher z renders on top.
     *
     * @param id stable identifier for this layer (replay/management)
     * @param z z-order (higher renders on top)
     * @param painter draw callback (every frame, main thread); first arg =
     *                tick count, second arg = draw context
     */
    void addLayer(String id, int z, BiConsumer<Integer, DrawContext2d> painter);

    /** Removes a previously registered layer. */
    void removeLayer(String id);

    /** Returns all registered layer identifiers in render order. */
    Supplier<List<String>> layerIds();
}
