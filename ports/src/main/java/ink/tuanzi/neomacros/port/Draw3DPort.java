package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ShapeSpec;

import java.util.List;
import java.util.function.Supplier;

/**
 * World-space 3D rendering: wireframe boxes, lines, and block highlights.
 * Layers persist across frames until removed; coordinates are world-space.
 *
 * <p>Shapes are keyed per layer: retained components (G-033) write through
 * {@link #putShape(String, String, ShapeSpec)} and mutate in place by
 * re-putting the same {@code shapeId}; the {@code drawBox}-style primitives
 * stay as convenience faces whose implementations append under a fresh
 * auto-generated id. Implementations must be safe for concurrent use from
 * script threads while the render thread reads.</p>
 */
public interface Draw3DPort {

    /**
     * Creates or replaces one keyed shape on a layer. Re-putting an existing
     * {@code shapeId} replaces that shape in place (upsert).
     *
     * @param layerId opaque layer key (owner-namespaced by the facade)
     * @param shapeId stable per-layer shape key
     * @param spec    immutable shape description to draw from now on
     */
    default void putShape(String layerId, String shapeId, ShapeSpec spec) {
        throw new UnsupportedPortFeature("putShape");
    }

    /** Removes one keyed shape; unknown layer/shape ids are ignored. */
    default void removeShape(String layerId, String shapeId) {
        throw new UnsupportedPortFeature("removeShape");
    }

    /** Draws a wireframe box between two world positions. */
    void drawBox(String layerId, Pos3d min, Pos3d max, int argbColor);

    /** Draws a line between two world positions. */
    void drawLine(String layerId, Pos3d from, Pos3d to, int argbColor);

    /**
     * Draws a live line from the local player's eyes to the entity behind
     * the UUID, re-resolved every frame while the entity is loaded.
     */
    void addTraceLine(String layerId, java.util.UUID entityUuid, int argbColor);

    /** Draws a filled convex surface through the given vertices (three or more). */
    void drawSurface(String layerId, List<Pos3d> points, int argbColor);

    /** Removes all shapes for one layer. */
    void clearLayer(String layerId);

    /** Returns all layers that currently hold shapes. */
    Supplier<List<String>> layerIds();
}
