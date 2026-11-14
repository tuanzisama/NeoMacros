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
 * Retained components cover G-033 {@code Box}/{@code Line3D} and G-033B
 * {@code TraceLine}/{@code EntityTraceLine}/{@code Surface}; the legacy
 * {@code Trace}/{@code Surface} records serve the frozen immediate
 * primitives.
 *
 * <p>Colours are packed ARGB ints. {@code cull} selects the depth-testing
 * bucket: {@code true} draws with world depth (terrain can hide the shape),
 * {@code false} draws over the world.</p>
 */
public sealed interface ShapeSpec
        permits ShapeSpec.Box, ShapeSpec.Line, ShapeSpec.Trace, ShapeSpec.Surface,
        ShapeSpec.CameraTrace, ShapeSpec.EntityTrace, ShapeSpec.Panel {

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

    /**
     * A live line from the camera to a fixed world-space target; the adapter
     * re-reads the camera position every frame while the target stays put.
     *
     * @param target the fixed world-space target endpoint
     * @param cull whether the line is depth tested against world geometry
     */
    record CameraTrace(Pos3d target, int argbColor, boolean cull) implements ShapeSpec {
    }

    /**
     * A live line from the camera to an entity behind the UUID plus a
     * vertical offset, re-resolved by the adapter every frame while the
     * entity loads; a null UUID draws nothing.
     *
     * @param entityUuid followed entity, or null while no entity is bound
     * @param yOffset vertical offset above the entity's position, in blocks
     * @param cull whether the line is depth tested against world geometry
     */
    record EntityTrace(UUID entityUuid, double yOffset, int argbColor, boolean cull)
            implements ShapeSpec {
    }

    /**
     * A rectangular panel placed in the world (the retained {@code Surface}
     * component). The adapter re-derives its four corners every frame —
     * from {@code origin} while unbound, or from the followed entity's
     * position plus {@code boundOffset} while one is set — applying the
     * rotations in y, x, then z order (degrees); {@code rotateToPlayer}
     * overwrites them with a camera-facing billboard each frame.
     *
     * @param origin panel corner, or its centre when {@code rotateCenter}
     * @param boundOffset offset added to the bound entity's position
     * @param boundEntity followed entity, or null for a fixed panel
     * @param argbColor flat panel colour (the component face has no colour
     *                  member; panels render opaque white)
     * @param renderBack whether the far side is drawn
     * @param cull whether the panel is depth tested against world geometry
     */
    record Panel(Pos3d origin, double width, double height,
                 double xRot, double yRot, double zRot,
                 boolean rotateCenter, boolean rotateToPlayer,
                 UUID boundEntity, Pos3d boundOffset,
                 int argbColor, boolean renderBack, boolean cull) implements ShapeSpec {
    }
}
