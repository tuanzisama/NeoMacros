package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ShapeSpec;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * World-space shape rendering: shapes are keyed per layer and re-projected
 * every frame — 26.x nodes emit through the vanilla gizmo pipeline (one
 * resident gizmo per depth bucket re-draws all layers each frame), 1.x
 * nodes emit into the world render through Fabric's WorldRenderEvents.
 * Script threads mutate the keyed spec maps while the render thread reads
 * them.
 *
 * <p>Depth buckets: shapes with {@code cull} (or the legacy primitives,
 * which behave as depth-tested) emit through the standard gizmo; shapes
 * with {@code alwaysOnTop} semantics ({@code cull == false}) emit through a
 * second gizmo registered with {@code setAlwaysOnTop()} — javap shows
 * {@code LevelRenderer} collects gizmos into separate standard /
 * alwaysOnTop primitive buckets. The 1.x pipeline has no always-on-top
 * variant of {@code RenderType.lines()}, so it draws every line depth-
 * tested and only honours {@code fill}.</p>
 */
public final class FabricDraw3DPort implements Draw3DPort {

    private static final float LINE_WIDTH = 2.0f;

    /** Auto id sequence for the immediate {@code drawBox}-style face. */
    private final AtomicLong legacySeq = new AtomicLong();

    /** layerId -> (shapeId -> current spec). */
    private final Map<String, Map<String, ShapeSpec>> layers = new ConcurrentHashMap<>();

    //? if >=26.1 {
    /** Standard-depth frame gizmo: shapes with {@code cull} (and legacy primitives). */
    private final Gizmo frameGizmo = (Gizmo) (primitives, partialTick) ->
            emitAll(primitives, false);

    /** Always-on-top frame gizmo: shapes drawn over the world ({@code cull == false}). */
    private final Gizmo frameGizmoTop = (Gizmo) (primitives, partialTick) ->
            emitAll(primitives, true);
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

    /** Hands the port's gizmos to the collector active for the current frame. */
    void addFrameGizmo() {
        //? if >=26.1 {
        Gizmos.addGizmo(frameGizmo);
        Gizmos.addGizmo(frameGizmoTop).setAlwaysOnTop();
        //?}
    }

    // ---- keyed shape face (retained components write through this) --------

    @Override
    public void putShape(String layerId, String shapeId, ShapeSpec spec) {
        layers.computeIfAbsent(layerId, key -> new ConcurrentHashMap<>())
                .put(shapeId, spec);
    }

    @Override
    public void removeShape(String layerId, String shapeId) {
        Map<String, ShapeSpec> shapes = layers.get(layerId);
        if (shapes == null) {
            return;
        }
        shapes.remove(shapeId);
        if (shapes.isEmpty()) {
            layers.remove(layerId, shapes);
        }
    }

    // ---- immediate primitive face (compat: appends under a fresh id) ------

    @Override
    public void drawBox(String layerId, Pos3d min, Pos3d max, int argbColor) {
        // Legacy primitives keep their historical depth-tested behaviour.
        putShape(layerId, nextLegacyId(),
                new ShapeSpec.Box(min, max, argbColor, 0, false, true));
    }

    @Override
    public void drawLine(String layerId, Pos3d from, Pos3d to, int argbColor) {
        putShape(layerId, nextLegacyId(), new ShapeSpec.Line(from, to, argbColor, true));
    }

    @Override
    public void addTraceLine(String layerId, java.util.UUID entityUuid, int argbColor) {
        putShape(layerId, nextLegacyId(), new ShapeSpec.Trace(entityUuid, argbColor));
    }

    @Override
    public void drawSurface(String layerId, java.util.List<Pos3d> points, int argbColor) {
        putShape(layerId, nextLegacyId(),
                new ShapeSpec.Surface(java.util.List.copyOf(points), argbColor));
    }

    @Override
    public void clearLayer(String layerId) {
        layers.remove(layerId);
    }

    @Override
    public Supplier<List<String>> layerIds() {
        return () -> List.copyOf(layers.keySet());
    }

    /** One fresh auto id for the immediate primitive face. */
    private String nextLegacyId() {
        return "r" + legacySeq.incrementAndGet();
    }

    //? if >=26.1 {
    /** Emits every layer's shapes in one depth bucket for one frame. */
    private void emitAll(GizmoPrimitives primitives, boolean alwaysOnTopBucket) {
        for (Map<String, ShapeSpec> shapes : layers.values()) {
            for (ShapeSpec spec : shapes.values()) {
                if (alwaysOnTop(spec) == alwaysOnTopBucket) {
                    emitShape(primitives, spec);
                }
            }
        }
    }

    /**
     * Bucket selector: boxes, lines and the retained traces/panels draw
     * over the world when they opt out of depth testing; the legacy
     * {@code Trace}/{@code Surface} primitives (which behave as depth-
     * tested) stay in the standard bucket exactly as before the buckets
     * existed.
     */
    private static boolean alwaysOnTop(ShapeSpec spec) {
        if (spec instanceof ShapeSpec.Box box) {
            return !box.cull();
        }
        if (spec instanceof ShapeSpec.Line line) {
            return !line.cull();
        }
        if (spec instanceof ShapeSpec.CameraTrace trace) {
            return !trace.cull();
        }
        if (spec instanceof ShapeSpec.EntityTrace trace) {
            return !trace.cull();
        }
        if (spec instanceof ShapeSpec.Panel panel) {
            return !panel.cull();
        }
        return false;
    }

    /** Emits one shape's gizmo primitives. */
    private static void emitShape(GizmoPrimitives primitives, ShapeSpec spec) {
        if (spec instanceof ShapeSpec.Line line) {
            primitives.addLine(vec(line.from()), vec(line.to()), line.argbColor(), LINE_WIDTH);
        } else if (spec instanceof ShapeSpec.Box box) {
            emitBoxEdges(primitives, box.min(), box.max(), box.argbColor());
            if (box.fill()) {
                emitBoxFill(primitives, box.min(), box.max(), box.argbFillColor());
            }
        } else if (spec instanceof ShapeSpec.CameraTrace trace) {
            float[] camera = cameraPose();
            primitives.addLine(new Vec3(camera[0], camera[1], camera[2]),
                    vec(trace.target()), trace.argbColor(), LINE_WIDTH);
        } else if (spec instanceof ShapeSpec.EntityTrace trace) {
            Vec3 target = entityTraceTarget(trace);
            if (target != null) {
                float[] camera = cameraPose();
                primitives.addLine(new Vec3(camera[0], camera[1], camera[2]),
                        target, trace.argbColor(), LINE_WIDTH);
            }
        } else if (spec instanceof ShapeSpec.Panel panel) {
            Vec3[] corners = panelCorners(panel);
            if (corners != null && panelFacesCamera(panel, corners)) {
                primitives.addTriangleFan(corners, panel.argbColor());
            }
        } else if (spec instanceof ShapeSpec.Trace trace) {
            Vec3[] endpoints = traceEndpoints(trace.entityUuid());
            if (endpoints != null) {
                primitives.addLine(endpoints[0], endpoints[1], trace.argbColor(), LINE_WIDTH);
            }
        } else if (spec instanceof ShapeSpec.Surface surface) {
            java.util.List<Pos3d> points = surface.points();
            Vec3[] fan = new Vec3[points.size()];
            for (int i = 0; i < fan.length; i++) {
                fan[i] = vec(points.get(i));
            }
            primitives.addTriangleFan(fan, surface.argbColor());
        }
    }

    /** Resolves player-eyes → entity-eyes endpoints for one trace, or null. */
    private static Vec3[] traceEndpoints(java.util.UUID uuid) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (level == null || player == null) {
            return null;
        }
        for (net.minecraft.world.entity.Entity entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) {
                return new Vec3[] {player.getEyePosition(), entity.getEyePosition()};
            }
        }
        return null;
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

    /**
     * Emits the box interior as six quads. The corner orders mirror vanilla
     * {@code CuboidGizmo}'s fill faces (javap of 26.3) so winding matches
     * the pipeline's expectations; corners are normalised per axis so an
     * unordered corner pair still fills the same volume the edges span.
     */
    private static void emitBoxFill(GizmoPrimitives primitives, Pos3d min, Pos3d max, int color) {
        double mx = Math.min(min.x(), max.x());
        double my = Math.min(min.y(), max.y());
        double mz = Math.min(min.z(), max.z());
        double Mx = Math.max(min.x(), max.x());
        double My = Math.max(min.y(), max.y());
        double Mz = Math.max(min.z(), max.z());
        primitives.addQuad(new Vec3(Mx, my, mz), new Vec3(Mx, My, mz),
                new Vec3(Mx, My, Mz), new Vec3(Mx, my, Mz), color);
        primitives.addQuad(new Vec3(mx, my, mz), new Vec3(mx, my, Mz),
                new Vec3(mx, My, Mz), new Vec3(mx, My, mz), color);
        primitives.addQuad(new Vec3(mx, my, mz), new Vec3(mx, My, mz),
                new Vec3(Mx, My, mz), new Vec3(Mx, my, mz), color);
        primitives.addQuad(new Vec3(mx, my, Mz), new Vec3(Mx, my, Mz),
                new Vec3(Mx, My, Mz), new Vec3(mx, My, Mz), color);
        primitives.addQuad(new Vec3(mx, My, mz), new Vec3(mx, My, Mz),
                new Vec3(Mx, My, Mz), new Vec3(Mx, My, mz), color);
        primitives.addQuad(new Vec3(mx, my, mz), new Vec3(Mx, my, mz),
                new Vec3(Mx, my, Mz), new Vec3(mx, my, Mz), color);
    }

    private static Vec3 vec(Pos3d value) {
        return new Vec3(value.x(), value.y(), value.z());
    }
    //?} else {
    /*
    // Renders every layer's shapes as world-space geometry for one frame.
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
        VertexConsumer fill = null;
        for (Map<String, ShapeSpec> shapes : layers.values()) {
            for (ShapeSpec spec : shapes.values()) {
                if (spec instanceof ShapeSpec.Line line) {
                    addWorldLine(buffer, pose, line.from(), line.to(), line.argbColor());
                } else if (spec instanceof ShapeSpec.Box box) {
                    addWorldBoxEdges(buffer, pose, box.min(), box.max(), box.argbColor());
                    if (box.fill()) {
                        if (fill == null) {
                            fill = consumers.getBuffer(RenderType.debugQuads());
                        }
                        addWorldBoxFill(fill, pose, box.min(), box.max(), box.argbFillColor());
                    }
                } else if (spec instanceof ShapeSpec.CameraTrace trace) {
                    float[] camera = cameraPose();
                    addWorldLine(buffer, pose,
                            new double[] {camera[0], camera[1], camera[2]},
                            new double[] {trace.target().x(), trace.target().y(), trace.target().z()},
                            trace.argbColor());
                } else if (spec instanceof ShapeSpec.EntityTrace trace) {
                    var target = entityTraceTarget(trace);
                    if (target != null) {
                        float[] camera = cameraPose();
                        addWorldLine(buffer, pose,
                                new double[] {camera[0], camera[1], camera[2]},
                                new double[] {target.x, target.y, target.z},
                                trace.argbColor());
                    }
                } else if (spec instanceof ShapeSpec.Panel panel) {
                    var corners = panelCorners(panel);
                    if (corners != null && panelFacesCamera(panel, corners)) {
                        for (int i = 0; i < corners.length; i++) {
                            var a = corners[i];
                            var b = corners[(i + 1) % corners.length];
                            addWorldLine(buffer, pose,
                                    new double[] {a.x, a.y, a.z},
                                    new double[] {b.x, b.y, b.z},
                                    panel.argbColor());
                        }
                    }
                } else if (spec instanceof ShapeSpec.Trace trace) {
                    var level = net.minecraft.client.Minecraft.getInstance().level;
                    var player = net.minecraft.client.Minecraft.getInstance().player;
                    if (level != null && player != null) {
                        for (net.minecraft.world.entity.Entity entity : level.entitiesForRendering()) {
                            if (entity.getUUID().equals(trace.entityUuid())) {
                                var eye = player.getEyePosition();
                                var other = entity.getEyePosition();
                                addWorldLine(buffer, pose,
                                        new double[] {eye.x, eye.y, eye.z},
                                        new double[] {other.x, other.y, other.z},
                                        trace.argbColor());
                                break;
                            }
                        }
                    }
                } else if (spec instanceof ShapeSpec.Surface surface) {
                    java.util.List<Pos3d> points = surface.points();
                    for (int i = 0; i < points.size(); i++) {
                        Pos3d a = points.get(i);
                        Pos3d b = points.get((i + 1) % points.size());
                        addWorldLine(buffer, pose, a, b, surface.argbColor());
                    }
                }
            }
        }
    }

    private static void addWorldBoxEdges(VertexConsumer buffer, Matrix4f pose,
            Pos3d min, Pos3d max, int color) {
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
            addWorldLine(buffer, pose, corners[edge[0]], corners[edge[1]], color);
        }
    }

    // Fills the box interior with six quads; the 1.x line render type
    // cannot draw triangles, so this goes through the position-colour
    // debug quads type (alpha/depth presentation pending observe run).
    private static void addWorldBoxFill(VertexConsumer fill, Matrix4f pose,
            Pos3d min, Pos3d max, int color) {
        double mx = Math.min(min.x(), max.x());
        double my = Math.min(min.y(), max.y());
        double mz = Math.min(min.z(), max.z());
        double Mx = Math.max(min.x(), max.x());
        double My = Math.max(min.y(), max.y());
        double Mz = Math.max(min.z(), max.z());
        double[][][] faces = {
                {{Mx, my, mz}, {Mx, My, mz}, {Mx, My, Mz}, {Mx, my, Mz}},
                {{mx, my, mz}, {mx, my, Mz}, {mx, My, Mz}, {mx, My, mz}},
                {{mx, my, mz}, {mx, My, mz}, {Mx, My, mz}, {Mx, my, mz}},
                {{mx, my, Mz}, {Mx, my, Mz}, {Mx, My, Mz}, {mx, My, Mz}},
                {{mx, My, mz}, {mx, My, Mz}, {Mx, My, Mz}, {Mx, My, mz}},
                {{mx, my, mz}, {Mx, my, mz}, {Mx, my, Mz}, {mx, my, Mz}},
        };
        for (double[][] face : faces) {
            for (double[] corner : face) {
                fill.addVertex(pose, (float) corner[0], (float) corner[1], (float) corner[2])
                        .setColor(color);
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

    // ---- retained-trace / panel helpers (shared by both pipelines) -------

    //? if >=26.2 {
    /** The main camera (26.2+ renamed {@code getMainCamera} to {@code mainCamera}). */
    private static net.minecraft.client.Camera mainCamera() {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera();
    }
    //?} else {
    /*
    // The main camera (accessor unchanged through 26.1.x and 1.x).
    private static net.minecraft.client.Camera mainCamera() {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
    }
    */
    //?}

    //? if >=26.1 {
    /**
     * Camera position and rotation for the current frame as
     * {@code [x, y, z, yaw, pitch]}; 26.x renamed the camera accessors
     * from the 1.x {@code getPosition()}/{@code getYRot()}/{@code getXRot()}
     * to {@code position()}/{@code yRot()}/{@code xRot()}.
     */
    private static float[] cameraPose() {
        var camera = mainCamera();
        Vec3 position = camera.position();
        return new float[] {
                (float) position.x, (float) position.y, (float) position.z,
                camera.yRot(), camera.xRot()};
    }
    //?} else {
    /*
    // Camera position and rotation for the current frame as
    // [x, y, z, yaw, pitch] (1.x camera accessors).
    private static float[] cameraPose() {
        var camera = mainCamera();
        Vec3 position = camera.getPosition();
        return new float[] {
                (float) position.x, (float) position.y, (float) position.z,
                camera.getYRot(), camera.getXRot()};
    }
    */
    //?}

    /** Resolves an entity's world position by UUID, or null when absent. */
    private static Vec3 entityPosition(java.util.UUID uuid) {
        if (uuid == null) {
            return null;
        }
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        for (net.minecraft.world.entity.Entity entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) {
                return entity.position();
            }
        }
        return null;
    }

    /** The followed entity's position plus its yOffset, or null when absent. */
    private static Vec3 entityTraceTarget(ShapeSpec.EntityTrace trace) {
        Vec3 position = entityPosition(trace.entityUuid());
        return position == null ? null : position.add(0, trace.yOffset(), 0);
    }

    /**
     * Derives one panel's four world-space corners for this frame, or
     * null when a bound entity is missing. Rotations are applied y, then
     * x, then z (degrees); {@code rotateToPlayer} overwrites them with a
     * camera-facing billboard. {@code pos} is the panel corner, or its
     * centre while {@code rotateCenter} is on.
     */
    private static Vec3[] panelCorners(ShapeSpec.Panel panel) {
        Vec3 origin;
        if (panel.boundEntity() != null) {
            Vec3 position = entityPosition(panel.boundEntity());
            if (position == null) {
                return null;
            }
            Pos3d offset = panel.boundOffset();
            origin = position.add(offset.x(), offset.y(), offset.z());
        } else {
            Pos3d corner = panel.origin();
            origin = new Vec3(corner.x(), corner.y(), corner.z());
        }

        double halfWidth = panel.width() / 2;
        double halfHeight = panel.height() / 2;
        double[][] local;
        if (panel.rotateCenter()) {
            local = new double[][] {
                    {-halfWidth, -halfHeight}, {halfWidth, -halfHeight},
                    {halfWidth, halfHeight}, {-halfWidth, halfHeight}};
        } else {
            local = new double[][] {
                    {0, 0}, {panel.width(), 0},
                    {panel.width(), panel.height()}, {0, panel.height()}};
        }

        Vec3[] corners = new Vec3[4];
        if (panel.rotateToPlayer()) {
            float[] camera = cameraPose();
            Vec3 toward = new Vec3(camera[0], camera[1], camera[2]).subtract(origin);
            if (toward.x == 0 && toward.y == 0 && toward.z == 0) {
                return null;
            }
            toward = toward.normalize();
            Vec3 right = new Vec3(0, 1, 0).cross(toward);
            if (right.x == 0 && right.y == 0 && right.z == 0) {
                right = new Vec3(1, 0, 0); // camera directly above/below
            } else {
                right = right.normalize();
            }
            Vec3 up = toward.cross(right).normalize();
            for (int i = 0; i < corners.length; i++) {
                corners[i] = origin.add(right.scale(local[i][0]).add(up.scale(local[i][1])));
            }
            return corners;
        }

        double yaw = Math.toRadians(panel.yRot());
        double pitch = Math.toRadians(panel.xRot());
        double roll = Math.toRadians(panel.zRot());
        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);
        double cx = Math.cos(pitch);
        double sx = Math.sin(pitch);
        double cz = Math.cos(roll);
        double sz = Math.sin(roll);
        for (int i = 0; i < corners.length; i++) {
            double x = local[i][0];
            double y = local[i][1];
            // y rotation, then x rotation, then z rotation (JsMacros order)
            double x1 = x * cy;
            double z1 = -x * sy;
            double y2 = y * cx - z1 * sx;
            double z2 = y * sx + z1 * cx;
            double x3 = x1 * cz - y2 * sz;
            double y3 = x1 * sz + y2 * cz;
            corners[i] = new Vec3(origin.x + x3, origin.y + y3, origin.z + z2);
        }
        return corners;
    }

    /**
     * Whether the camera sees the panel's front side: always for a
     * billboard or a rendered back, otherwise via the face normal.
     */
    private static boolean panelFacesCamera(ShapeSpec.Panel panel, Vec3[] corners) {
        if (panel.renderBack() || panel.rotateToPlayer()) {
            return true;
        }
        Vec3 first = corners[1].subtract(corners[0]);
        Vec3 second = corners[2].subtract(corners[0]);
        Vec3 normal = first.cross(second);
        float[] camera = cameraPose();
        Vec3 toward = new Vec3(camera[0], camera[1], camera[2]).subtract(corners[0]);
        return normal.dot(toward) > 0;
    }
}
