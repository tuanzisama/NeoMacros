package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.model.Pos3d;
import net.minecraft.world.phys.Vec3;

//? if >=26.1 {
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.Gizmos;
//?} else {
/*
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
*/
//?}

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * World-space shape rendering: 26.x nodes emit through the vanilla gizmo
 * pipeline (one gizmo re-draws every layer each frame); 1.x nodes emit
 * lines into the world render through Fabric's WorldRenderEvents. Script
 * threads mutate copy-on-write shape lists while the render thread reads
 * them.
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

    //? if >=26.1 {
    /** The persistent frame gizmo, re-added into vanilla's collector every frame. */
    private final Gizmo frameGizmo = (Gizmo) (primitives, partialTick) -> emitAll(primitives);
    //?}

    /** Creates the port and registers its renderer with the active pipeline. */
    public FabricDraw3DPort() {
        //? if >=26.1 {
        Draw3DFrameHub.register(this);
        //?} else {
        /*
        WorldRenderEvents.AFTER_ENTITIES.register(this::emitWorld);
        */
        //?}
    }

    /** Hands the port's gizmo to the collector active for the current frame. */
    void addFrameGizmo() {
        //? if >=26.1 {
        Gizmos.addGizmo(frameGizmo);
        //?}
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

    //? if >=26.1 {
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

    private static Vec3 vec(Pos3d value) {
        return new Vec3(value.x(), value.y(), value.z());
    }
    //?} else {
    /*
    /** Renders every layer's shapes as world-space lines for one frame. *^/
    private void emitWorld(WorldRenderContext context) {
        var consumers = context.consumers();
        if (consumers == null) {
            return;
        }
        var matrices = context.matrixStack();
        if (matrices == null) {
            return;
        }
        var buffer = consumers.getBuffer(RenderType.lines());
        var pose = matrices.last().pose();
        for (CopyOnWriteArrayList<Shape> shapes : layers.values()) {
            for (Shape shape : shapes) {
                if (shape instanceof Line(Pos3d from, Pos3d to, int color)) {
                    addWorldLine(buffer, pose, from, to, color);
                } else if (shape instanceof Box(Pos3d min, Pos3d max, int color)) {
                    double x1 = min.x(), y1 = min.y(), z1 = min.z();
                    double x2 = max.x(), y2 = max.y(), z2 = max.z();
                    double[][] corners = {
                            {x1, y1, z1}, {x2, y1, z1},
                            {x2, y1, z2}, {x1, y1, z2},
                            {x1, y2, z1}, {x2, y2, z1},
                            {x2, y2, z2}, {x1, y2, z2},
                    };
                    int[][] edges = {
                            {0, 1}, {1, 2}, {2, 3}, {3, 0},
                            {4, 5}, {5, 6}, {6, 7}, {7, 4},
                            {0, 4}, {1, 5}, {2, 6}, {3, 7},
                    };
                    for (int[] edge : edges) {
                        addWorldLine(buffer, pose,
                                corners[edge[0]], corners[edge[1]], color);
                    }
                }
            }
        }
    }

    private static void addWorldLine(VertexConsumer buffer, Matrix4f pose,
            Pos3d from, Pos3d to, int color) {
        addWorldPoint(buffer, pose, from.x(), from.y(), from.z(), color);
        addWorldPoint(buffer, pose, to.x(), to.y(), to.z(), color);
    }

    private static void addWorldLine(VertexConsumer buffer, Matrix4f pose,
            double[] from, double[] to, int color) {
        addWorldPoint(buffer, pose, from[0], from[1], from[2], color);
        addWorldPoint(buffer, pose, to[0], to[1], to[2], color);
    }

    private static void addWorldPoint(VertexConsumer buffer, Matrix4f pose,
            double x, double y, double z, int color) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(color)
                .setNormal(0f, 1f, 0f);
    }
    */
    //?}
}
