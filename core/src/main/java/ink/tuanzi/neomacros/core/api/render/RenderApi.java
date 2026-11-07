package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.List;

/**
 * The {@code neo.render} module: world-space shape drawing on top of the
 * vanilla gizmo pipeline. Shapes persist across frames until cleared or the
 * module unloads; coordinates are world coordinates.
 *
 * @zh {@code neo.render}：基于原版 gizmo 管线的世界坐标图形绘制。图形跨帧保留，直到清空或模块卸载；坐标为世界坐标。
 */
@ApiModule(name = "render", category = "界面")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class RenderApi {

    private final Draw3DPort draw3d;

    /** Creates the API over the world-space render port (bootstrap-provided). */
    public RenderApi(Draw3DPort draw3d) {
        this.draw3d = draw3d;
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
        draw3d.drawBox(layerId,
                new Pos3d(min.getX(), min.getY(), min.getZ()),
                new Pos3d(max.getX(), max.getY(), max.getZ()), argbColor);
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
        draw3d.drawLine(layerId,
                new Pos3d(from.getX(), from.getY(), from.getZ()),
                new Pos3d(to.getX(), to.getY(), to.getZ()), argbColor);
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
        draw3d.addTraceLine(layerId, snapshot.uuid(), argbColor);
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
        draw3d.drawSurface(layerId, vertices, argbColor);
    }

    /**
     * Removes all shapes on one layer.
     *
     * @zh 清空一个图层上的全部形状。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void clearLayer(String layerId) {
        draw3d.clearLayer(layerId);
    }

    /**
     * Lists all layers that currently hold shapes.
     *
     * @zh 列出当前持有形状的图层。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public List<String> layerIds() {
        return draw3d.layerIds().get();
    }
}
