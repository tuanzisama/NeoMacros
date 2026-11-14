package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.UUID;

/**
 * Neutral, immutable descriptions of one world-space shape as it should be
 * drawn this frame. The core side builds specs for retained components
 * (G-033 {@code Box}/{@code Line3D}) and for the immediate {@code drawBox}
 * style primitives; adapters translate them into their pipeline of choice
 * (gizmo primitives on 26.x, world buffers on 1.x). Specs carry no element
 * identity — {@code putShape(layerId, shapeId, spec)} supplies that key.
 *
 * <p>Colours are packed ARGB ints. {@code cull} selects the depth-testing
 * bucket: {@code true} draws with world depth (terrain can hide the shape),
 * {@code false} draws over the world.</p>
 */
public sealed interface ShapeSpec
        permits ShapeSpec.Box, ShapeSpec.Line, ShapeSpec.Trace, ShapeSpec.Surface {

    /**
     * An axis-aligned wireframe box with an optional filled interior.
     *
     * @param argbColor outline colour packed ARGB
     * @param argbFillColor interior colour packed ARGB, used only when
     *                       {@code fill} is {@code true}
     * @param fill whether to draw the six faces in addition to the edges
     * @param cull whether the shape is depth tested against world geometry
     */
    record Box(Pos3d min, Pos3d max, int argbColor, int argbFillColor,
               boolean fill, boolean cull) implements ShapeSpec {
    }

    /**
     * A straight line segment between two ordered endpoints.
     *
     * @param cull whether the line is depth tested against world geometry
     */
    record Line(Pos3d from, Pos3d to, int argbColor, boolean cull) implements ShapeSpec {
    }

    /**
     * A live line from the local player's eyes to the entity behind the
     * UUID, re-resolved by the adapter every frame while the entity loads.
     */
    record Trace(UUID entityUuid, int argbColor) implements ShapeSpec {
    }

    /**
     * A filled convex surface through at least three vertices.
     */
    record Surface(List<Pos3d> points, int argbColor) implements ShapeSpec {
    }
}
