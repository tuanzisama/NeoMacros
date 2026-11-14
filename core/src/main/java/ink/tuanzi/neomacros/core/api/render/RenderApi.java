package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ShapeSpec;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code neo.render} module: world-space shape drawing on top of the
 * vanilla gizmo pipeline. Two faces share one layer model: the immediate
 * primitives ({@link #drawBox}/{@link #drawLine}/…) accumulate
 * fire-and-forget shapes per layer, and {@link #layer(String)} hands out
 * retained {@link Draw3D} overlays whose elements are mutable handles.
 * Shapes persist across frames until cleared or the module unloads;
 * coordinates are world coordinates.
 *
 * <p>Layers are keyed per module owner ({@code <module>:<id>}); a module's
 * layers are cleared automatically when it unloads. Owner-less callers
 * (console, host code) keep the bare id, matching pre-owner behaviour.</p>
 *
 * @zh {@code neo.render}：基于原版 gizmo 管线的世界坐标图形绘制。即时原语与 {@code layer(id)} 返回的保留式 {@link Draw3D} 图层共享一层模型；图形跨帧保留，直到清空或模块卸载。图层按模块 owner 隔离，模块卸载时自动清空。
 */
@ApiModule(name = "render", category = "界面")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class RenderApi {

    private final Draw3DPort draw3d;

    /** Resolved overlays keyed by owner-namespaced port key. */
    private final Map<String, Draw3D> layers = new ConcurrentHashMap<>();

    /** The owner that registered the unload hook for each owned key. */
    private final Map<String, LoadedModule> keyOwners = new ConcurrentHashMap<>();

    /** Creates the API over the world-space render port (bootstrap-provided). */
    public RenderApi(Draw3DPort draw3d) {
        this.draw3d = draw3d;
    }

    /**
     * Returns (creating if needed) the retained overlay for one layer id.
     * Idempotent per owner: the same module calling with the same id always
     * gets the same handle, while two modules with the same public id get
     * isolated layers.
     *
     * @param id public layer id; ids beginning with {@code $} are reserved
     * @return the overlay handle for this layer
     * @throws IllegalArgumentException for empty or {@code $}-prefixed ids
     * @zh 取得（必要时创建）图层句柄；同 owner 同 id 幂等，不同模块互不干扰。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw3D layer(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("layer id is required");
        }
        if (id.startsWith("$")) {
            throw new IllegalArgumentException(
                    "layer ids beginning with '$' are reserved");
        }
        return container(id);
    }

    /**
     * Every overlay this module (or, without an owner, any caller) has
     * touched, sorted by layer key. Layers stay listed after
     * {@link #clearLayer} — an empty layer is still a layer.
     *
     * @zh 当前全部图层句柄（按键排序）；清空后的空图层仍在列表中。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public List<Draw3D> getLayers() {
        return layers.values().stream()
                .sorted(Comparator.comparing(Draw3D::portKey))
                .toList();
    }

    /**
     * Draws a wireframe box between two world positions on one layer.
     *
     * @param layerId layer identifier; shapes accumulate per layer until
     *                {@link #clearLayer}
     * @param min     first corner (world coordinates)
     * @param max     opposite corner (world coordinates)
     * @param argbColor ARGB color (e.g. {@code 0xFF00FF00} for opaque green)
     * @zh 在世界内绘制线框方块。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void drawBox(String layerId, ink.tuanzi.neomacros.core.api.types.Pos3D min,
            ink.tuanzi.neomacros.core.api.types.Pos3D max, int argbColor) {
        container(layerId).addRaw(new ShapeSpec.Box(
                new Pos3d(min.getX(), min.getY(), min.getZ()),
                new Pos3d(max.getX(), max.getY(), max.getZ()),
                argbColor, 0, false, true));
    }

    /**
     * Draws a line between two world positions on one layer.
     *
     * @zh 在世界内绘制线段。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
        public void drawLine(String layerId, ink.tuanzi.neomacros.core.api.types.Pos3D from,
            ink.tuanzi.neomacros.core.api.types.Pos3D to, int argbColor) {
        container(layerId).addRaw(new ShapeSpec.Line(
                new Pos3d(from.getX(), from.getY(), from.getZ()),
                new Pos3d(to.getX(), to.getY(), to.getZ()),
                argbColor, true));
    }

    /**
     * Draws a live line from the local player's eyes to the entity, kept
     * aimed at it every frame while the entity stays loaded.
     *
     * @zh 绘制一条从本地玩家眼睛到实体的活动连线，实体在场景中时每帧保持指向。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void traceLine(String layerId, ink.tuanzi.neomacros.core.api.entity.EntityApi.Entity target,
            int argbColor) {
        ink.tuanzi.neomacros.port.model.EntitySnapshot snapshot =
                target == null ? null : target.snapshot();
        if (snapshot == null) {
            throw new IllegalStateException("target entity is not loaded");
        }
        container(layerId).addRaw(new ShapeSpec.Trace(snapshot.uuid(), argbColor));
    }

    /**
     * Draws a filled convex surface through three or more vertices (a fan
     * from the first point on modern nodes; an outlined polygon on 1.x).
     *
     * @zh 绘制穿过三个及以上顶点的凸面（新节点为自首点的扇面；1.x 节点为多边形描边）。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void surface(String layerId, java.util.List<ink.tuanzi.neomacros.core.api.types.Pos3D> points,
            int argbColor) {
        if (points == null || points.size() < 3) {
            throw new IllegalArgumentException("a surface needs at least three points");
        }
        java.util.List<Pos3d> vertices = new java.util.ArrayList<>(points.size());
        for (ink.tuanzi.neomacros.core.api.types.Pos3D point : points) {
            vertices.add(new Pos3d(point.getX(), point.getY(), point.getZ()));
        }
        container(layerId).addRaw(new ShapeSpec.Surface(vertices, argbColor));
    }

    /**
     * Removes all shapes on one layer. Layers with no shapes yet are left
     * untouched — clearing never creates a layer.
     *
     * @zh 清空一个图层上的全部形状；不会为不存在的图层创建句柄。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void clearLayer(String layerId) {
        String key = resolveKey(layerId);
        Draw3D handle = layers.get(key);
        if (handle != null) {
            handle.clear();
        } else {
            draw3d.clearLayer(key);
        }
    }

    // ---- owner scoping -----------------------------------------------------

    /** The overlay for one public id, creating it and its unload hook. */
    private Draw3D container(String publicId) {
        String key = resolveKey(publicId);
        return layers.computeIfAbsent(key, k -> new Draw3D(k, publicId, draw3d));
    }

    /**
     * Resolves the port key for a public id: {@code <module>:<id>} under a
     * loading module, the bare id otherwise. The first touch of an owned
     * key registers one unload hook so the module's layers clear with it.
     */
    private String resolveKey(String publicId) {
        LoadedModule owner = LoadTracker.current();
        if (owner == null) {
            return publicId;
        }
        String key = owner.entry().name() + ":" + publicId;
        if (keyOwners.putIfAbsent(key, owner) == null) {
            owner.onRelease(() -> releaseOwned(key, owner));
        }
        return key;
    }

    /** Drops one owned key when its owning module unloads (guarded). */
    private void releaseOwned(String key, LoadedModule owner) {
        if (keyOwners.remove(key, owner)) {
            layers.remove(key);
            draw3d.clearLayer(key);
        }
    }
}
