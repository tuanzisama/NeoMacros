package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.model.Pos3d;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * World-space shape rendering over the vanilla gizmo pipeline. One gizmo is
 * registered for the whole port and re-emits every layer's shapes each
 * frame; script threads mutate copy-on-write shape lists while the render
 * thread reads them.
 */
public final class FabricDraw3DPort implements Draw3DPort {

    private static final float LINE_WIDTH = 2.0f;

    private sealed interface Shape permits Box, Line {
    }

    private record Box(Pos3d min, Pos3d max, int color) implements Shape {
    }

    private record Line(Pos3d from, Pos3d to, int color) implements Shape {
    }

    private final Map<String, CopyOnWriteArrayList<Shape>> layers = new ConcurrentHashMap<>();

    /** Creates the port and registers its gizmo with the vanilla pipeline. */
    public FabricDraw3DPort() {
        Gizmos.addGizmo((Gizmo) (primitives, partialTick) -> emitAll(primitives));
    }

    @Override
    public void drawBox(String layerId, Pos3d min, Pos3d max, int argbColor) {
        layers.computeIfAbsent(layerId, k -> new CopyOnWriteArrayList<>())
                .add(new Box(min, max, argbColor));
    }

    @Override
    public void drawLine(String layerId, Pos3d from, Pos3d to, int argbColor) {
        layers.computeIfAbsent(layerId, k -> new CopyOnWriteArrayList<>())
                .add(new Line(from, to, argbColor));
    }

    @Override
    public void clearLayer(String layerId) {
        layers.remove(layerId);
    }

    @Override
    public Supplier<List<String>> layerIds() {
        return () -> List.copyOf(layers.keySet());
    }

    /** Emits every layer's shapes as gizmo lines for one frame. */
    private void emitAll(GizmoPrimitives primitives) {
        for (CopyOnWriteArrayList<Shape> shapes : layers.values()) {
            for (Shape shape : shapes) {
                if (shape instanceof Line(Pos3d from, Pos3d to, int color)) {
                    primitives.addLine(vec(from), vec(to), color, LINE_WIDTH);
                } else if (shape instanceof Box(Pos3d min, Pos3d max, int color)) {
                    emitBoxEdges(primitives, min, max, color);
                }
            }
        }
    }

    /** Emits a box as its twelve wireframe edges. */
    private static void emitBoxEdges(GizmoPrimitives primitives, Pos3d min, Pos3d max, int color) {
        double x1 = min.x(), y1 = min.y(), z1 = min.z();
        double x2 = max.x(), y2 = max.y(), z2 = max.z();
        Vec3[] corners = {
                new Vec3(x1, y1, z1), new Vec3(x2, y1, z1),
                new Vec3(x2, y1, z2), new Vec3(x1, y1, z2),
                new Vec3(x1, y2, z1), new Vec3(x2, y2, z1),
                new Vec3(x2, y2, z2), new Vec3(x1, y2, z2),
        };
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7},
        };
        for (int[] edge : edges) {
            primitives.addLine(corners[edge[0]], corners[edge[1]], color, LINE_WIDTH);
        }
    }

    private static Vec3 vec(Pos3d pos) {
        return new Vec3(pos.x(), pos.y(), pos.z());
    }
}
