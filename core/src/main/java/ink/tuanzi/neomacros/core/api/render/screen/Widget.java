package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.Map;

/**
 * Skeleton of the live widget handle family (G-036): binds one control id
 * to its owning {@link BuiltScreen} and funnels reads through the session
 * cache and writes through {@link BuiltScreen#setControl}. The public
 * member face (position/message/active/visible/click/tooltip, the JsMacros
 * {@code ClickableWidgetHelper} account) is delivered by G-036B; this
 * skeleton exists now so the concrete handles and builders have their
 * Java-required base class.
 *
 * <p>After the session closes, reads answer neutral defaults (the cache is
 * empty) and every mutation throws through {@code setControl}.</p>
 *
 * @zh 活控件句柄族的骨架（G-036）：把一个控件 id 绑定到其所属 {@link BuiltScreen}，读走会话缓存、写走 {@link BuiltScreen#setControl}。公共成员面（位置/文本/active/visible/click/tooltip 等 JsMacros ClickableWidgetHelper 账）由 G-036B 补全；本骨架先落地以满足 Java 基类先行要求。会话关闭后读回中立默认值、写一律 throw。
 */
@ApiClass(name = "Widget", kind = ApiClassKind.HANDLE,
        wraps = "net.minecraft.client.gui.components.AbstractWidget")
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public class Widget implements ScriptHandle {

    final BuiltScreen screen;
    final String id;

    Widget(BuiltScreen screen, String id) {
        this.screen = screen;
        this.id = id;
    }

    /** The control id this handle is bound to.
     * @zh 该句柄绑定的控件 id。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public String getId() {
        return id;
    }

    /** The owning session handle.
     * @zh 所属会话句柄。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BuiltScreen getScreen() {
        return screen;
    }

    /** The live snapshot, or null when closed/unknown. */
    final ControlState state() {
        return screen.control(id);
    }

    /** Applies one whitelisted patch through the session (throws when closed). */
    final void write(String key, Object value) {
        screen.setControl(id, Map.of(key, value));
    }
}
