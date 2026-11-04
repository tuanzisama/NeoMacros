package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.List;

/**
 * The {@code neo.render} facade: world-space shape drawing on top of the
 * vanilla gizmo pipeline. Shapes persist across frames until cleared or the
 * module unloads; coordinates are world coordinates.
 *
 * @zh {@code neo.render} 门面：基于原版 gizmo 管线的世界坐标图形绘制。图形跨帧保留，直到清空或模块卸载；坐标为世界坐标。
 */
@ApiModule(name = "render", category = "界面")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class RenderApi {

    private final Draw3DPort draw3d;

    /** Creates the facade over the world-space render port (bootstrap-provided). */
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
    public void drawBox(String layerId, Pos3d min, Pos3d max, int argbColor) {
        draw3d.drawBox(layerId, min, max, argbColor);
    }

    /**
     * Draws a line between two world positions on one layer.
     *
     * @zh 在世界内绘制线段。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void drawLine(String layerId, Pos3d from, Pos3d to, int argbColor) {
        draw3d.drawLine(layerId, from, to, argbColor);
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
