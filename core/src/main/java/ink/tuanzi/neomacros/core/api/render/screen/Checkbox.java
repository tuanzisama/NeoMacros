package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

/**
 * Live handle for a {@code checkbox} control (JsMacros
 * {@code CheckBoxWidgetHelper}, Mojang spelling). State reads come from
 * the session cache (kept fresh by {@code stateChanged}); writes go
 * through the {@code setControl} whitelist and land on the live widget
 * without an event echo (the {@code CheckboxAccessor} path).
 *
 * @zh {@code checkbox} 控件的活句柄（JsMacros {@code CheckBoxWidgetHelper}，Mojang 拼写）。状态读自会话缓存（由 stateChanged 刷新）；写走 {@code setControl} 白名单就地落到活控件、无事件回声（CheckboxAccessor 通道）。
 */
@ApiClass(name = "Checkbox", kind = ApiClassKind.HANDLE,
        wraps = "net.minecraft.client.gui.components.Checkbox")
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public class Checkbox extends Widget<Checkbox> {

    Checkbox(BuiltScreen screen, String id) {
        super(screen, id);
    }

    /** Whether the box is currently checked ({@code isChecked}).
     * @zh 复选框当前是否勾选（{@code isChecked}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean isChecked() {
        ControlState state = state();
        return state != null && Boolean.TRUE.equals(state.checked());
    }

    /** Flips the checked state and returns this handle.
     * @zh 翻转勾选状态并返回自身。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Checkbox toggle() {
        write("checked", !isChecked());
        return this;
    }

    /** Sets the checked state and returns this handle.
     * @zh 设置勾选状态并返回自身。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Checkbox setChecked(@ApiType("boolean") boolean checked) {
        write("checked", checked);
        return this;
    }
}
