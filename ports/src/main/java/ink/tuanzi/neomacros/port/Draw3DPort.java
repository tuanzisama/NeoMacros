package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.List;
import java.util.function.Supplier;

/**
 * World-space 3D rendering: wireframe boxes, lines, and block highlights.
 * Layers persist across frames until removed; coordinates are world-space.
 */
public interface Draw3DPort {

    /** Draws a wireframe box between two world positions. */
    void drawBox(String layerId, Pos3d min, Pos3d max, int argbColor);

    /** Draws a line between two world positions. */
    void drawLine(String layerId, Pos3d from, Pos3d to, int argbColor);

    /** Removes all shapes for one layer. */
    void clearLayer(String layerId);

    /** Returns all layers that currently hold shapes. */
    Supplier<List<String>> layerIds();
}
