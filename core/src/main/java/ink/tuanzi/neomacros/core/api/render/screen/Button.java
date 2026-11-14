package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Live handle for a {@code button} (and {@code imageButton}) control on an
 * open {@link BuiltScreen} — the JsMacros {@code ButtonWidgetHelper} slot,
 * named with the Mojang word (G-003 §5.2 / WAVE-DECISIONS C⑤). The
 * button-specific and shared widget members ({@code click},
 * {@code message}/{@code label}, {@code active}, …) belong to the
 * {@code ClickableWidgetHelper} account and land with G-036B on the
 * {@link Widget} base; this class ships the typed binding now so
 * {@link BuiltScreen#button} can answer.
 *
 * @zh 打开的 {@link BuiltScreen} 上 {@code button}（含 {@code imageButton}）控件的活句柄——JsMacros {@code ButtonWidgetHelper} 对位，类名取 Mojang 词。按钮专属与共享成员（click/message/active 等）归 ClickableWidgetHelper 账、由 G-036B 在 {@link Widget} 基类落地；本类先交付类型化绑定。
 */
@ApiClass(name = "Button", kind = ApiClassKind.HANDLE,
        wraps = "net.minecraft.client.gui.components.Button")
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public class Button extends Widget<Button> {

    Button(BuiltScreen screen, String id) {
        super(screen, id);
    }
}
