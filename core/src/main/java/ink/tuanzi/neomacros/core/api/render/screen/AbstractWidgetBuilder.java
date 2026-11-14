package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Skeleton of the script control builders (G-036): the common fluent
 * chain (id/x/y/pos/width/height/size/zIndex/message/active/visible) plus
 * the terminal {@link #build()} that assembles a validated
 * {@link ControlSpec} — the JsMacros {@code createWidget()} slot (G-003
 * §5.2). Concrete builders contribute their type-specific fields through
 * {@link #contribute(Map)}.
 *
 * <p>G-003 §5.2 rules honored here: builders are root-constructed
 * ({@code new neo.ButtonBuilder()}, the JsMacros {@code screen.*
 * Builder()} factory fold), chain setters use the noun word, and there is
 * <b>no getter readback on the builder</b> — the engine would map a
 * no-arg {@code getX()}/{@code isX()} to the same script property as the
 * fluent {@code x(int)} method (one member per name), so readback lives on
 * the {@link ControlSpec} product instead. The remaining base members
 * (alpha/layout helpers) are the G-036B account.</p>
 *
 * @zh 脚本控件构建器骨架（G-036）：公共链式面（id/x/y/pos/width/height/size/zIndex/message/active/visible）加终结 {@link #build()}（产出已校验的 {@link ControlSpec}，即 JsMacros {@code createWidget()} 对位）。具体构建器经 {@link #contribute(Map)} 贡献类型专属字段。按 G-003 §5.2：根构造器 {@code new neo.ButtonBuilder()}、链式 setter 用名词原词、构建器不设属性回读（引擎会把无参 getX/isX 与链式 x(int) 映射到同一脚本属性）——回读走 build() 产物；其余基类成员（alpha/布局助手）属 G-036B 账。
 */
@ApiClass(name = "AbstractWidgetBuilder", kind = ApiClassKind.DATA)
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public abstract class AbstractWidgetBuilder<B extends AbstractWidgetBuilder<B>>
        implements ScriptHandle {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private String id;
    private int x;
    private int y;
    private int width;
    private int height;
    private int z;
    private String label;
    private Boolean active;
    private Boolean visible;

    /** The control type discriminator this builder produces. */
    protected abstract String type();

    /** Adds the type-specific fields (callbacks, checked, sprites, ...) to the raw spec. */
    protected abstract void contribute(Map<String, Object> raw);

    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

    /** Sets the control id (auto-generated when omitted at {@link #build()}).
     * @zh 设置控件 id（{@link #build()} 时缺省则自动生成）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B id(@ApiType("string") String controlId) {
        this.id = controlId;
        return self();
    }

    /** Sets the absolute x position.
     * @zh 设置绝对 x 坐标。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B x(@ApiType("number") int x) {
        this.x = x;
        return self();
    }

    /** Sets the absolute y position.
     * @zh 设置绝对 y 坐标。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B y(@ApiType("number") int y) {
        this.y = y;
        return self();
    }

    /** Sets both absolute coordinates.
     * @zh 同时设置绝对坐标。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B pos(@ApiType("number") int x, @ApiType("number") int y) {
        this.x = x;
        this.y = y;
        return self();
    }

    /** Sets the width.
     * @zh 设置宽度。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B width(@ApiType("number") int width) {
        this.width = width;
        return self();
    }

    /** Sets the height.
     * @zh 设置高度。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B height(@ApiType("number") int height) {
        this.height = height;
        return self();
    }

    /** Sets both dimensions.
     * @zh 同时设置宽高。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B size(@ApiType("number") int width, @ApiType("number") int height) {
        this.width = width;
        this.height = height;
        return self();
    }

    /** Sets the in-screen draw/hit ordering key (ties break by declaration).
     * @zh 设置屏内绘制/命中排序键（同值按声明序）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B zIndex(@ApiType("number") int z) {
        this.z = z;
        return self();
    }

    /** Sets the label text.
     * @zh 设置标签文本。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B message(@ApiType("string") String message) {
        this.label = message;
        return self();
    }

    /** Sets whether the control accepts input.
     * @zh 设置控件是否可交互。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B active(@ApiType("boolean") boolean active) {
        this.active = active;
        return self();
    }

    /** Sets whether the control is drawn.
     * @zh 设置控件是否绘制。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B visible(@ApiType("boolean") boolean visible) {
        this.visible = visible;
        return self();
    }

    /**
     * Assembles and validates the declarative control ({@code createWidget}
     * → {@code build()}); add the result through
     * {@link BuiltScreen#add(Map)}.
     *
     * @zh 组装并校验声明式控件（{@code createWidget()} → {@code build()}）；结果经 {@link BuiltScreen#add(Map)} 挂屏。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ControlSpec build() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("type", type());
        raw.put("id", id != null && !id.isEmpty()
                ? id : "ctl-" + SEQUENCE.incrementAndGet());
        raw.put("x", x);
        raw.put("y", y);
        raw.put("width", width);
        raw.put("height", height);
        raw.put("z", z);
        if (label != null) {
            raw.put("label", label);
        }
        if (active != null) {
            raw.put("active", active);
        }
        if (visible != null) {
            raw.put("visible", visible);
        }
        contribute(raw);
        return ControlSpec.parse(raw);
    }
}
