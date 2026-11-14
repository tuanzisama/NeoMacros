package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

/**
 * Live handle for a {@code lockButton} control (JsMacros
 * {@code LockButtonWidgetHelper}, Mojang word per G-003 §5.3). State
 * reads come from the session cache; {@code setLocked} lands on the live
 * {@code LockIconButton} without pressing it, so no callback runs (the
 * same no-echo semantics as the other handle writes).
 *
 * @zh {@code lockButton} 控件的活句柄（JsMacros {@code LockButtonWidgetHelper}，类名按 Mojang 词）。状态读自会话缓存；{@code setLocked} 就地写到活的 {@code LockIconButton}、不按压、不触发回调（与其他句柄写一致的无回声语义）。
 */
@ApiClass(name = "LockButton", kind = ApiClassKind.HANDLE,
        wraps = "net.minecraft.client.gui.components.LockIconButton")
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public class LockButton extends Widget<LockButton> {

    LockButton(BuiltScreen screen, String id) {
        super(screen, id);
    }

    /** Whether the button is currently locked ({@code isLocked}).
     * @zh 按钮当前是否锁定（{@code isLocked}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean isLocked() {
        ControlState state = state();
        return state != null && Boolean.TRUE.equals(state.locked());
    }

    /** Sets the locked state directly and returns this handle ({@code setLocked}).
     * @zh 直接设置锁定状态并返回自身（{@code setLocked}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public LockButton setLocked(@ApiType("boolean") boolean locked) {
        write("locked", locked);
        return this;
    }
}
