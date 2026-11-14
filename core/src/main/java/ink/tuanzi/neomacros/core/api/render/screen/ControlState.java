package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

/**
 * Read-only snapshot of one control on a {@link BuiltScreen}: layout plus
 * the live value fields that exist for its type (absent = undefined for
 * the guest). Snapshots come from the session cache (seeded from the spec,
 * refreshed by {@code stateChanged} writes) or a port read-back on a miss.
 *
 * @zh {@link BuiltScreen} 上一个控件的只读快照：布局加该类型存在的活值字段（缺席 = guest 侧 undefined）。快照来自会话缓存（以 spec 播种、由 stateChanged 写刷新）或缓存未命中时的端口回读。
 */
@ApiClass(name = "ControlState", kind = ApiClassKind.DATA)
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public record ControlState(
        @ApiType("string") String id,
        @ApiType("string") String type,
        int x,
        int y,
        int width,
        int height,
        int z,
        @ApiType("string") String label,
        boolean active,
        boolean visible,
        @ApiOptional @ApiType("boolean") Boolean checked,
        @ApiOptional @ApiType("number") Double value,
        @ApiOptional @ApiType("string") String text,
        @ApiOptional @ApiType("boolean") Boolean locked) {

    /** Converts one port-side snapshot into the script-facing record. */
    static ControlState from(ink.tuanzi.neomacros.port.ScreenPort.ControlState state) {
        return new ControlState(state.id(), state.type(), state.x(), state.y(),
                state.width(), state.height(), state.z(), state.label(),
                state.active(), state.visible(), state.checked(), state.value(),
                state.text(), state.locked());
    }
}
