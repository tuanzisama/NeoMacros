package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Declarative configuration for one script-built screen: title, lifecycle
 * flags, the ordered flat control list, and the seven screen-level callback
 * fields. Scripts pass a plain object literal to
 * {@code player.buildScreen}; the facade parses it into this record, keeps
 * the callbacks, and hands the adapter a callback-free neutral map.
 *
 * <p>Defaults follow the G-003 decisions: {@code showTitle=true},
 * {@code pause=false} (a pausing screen would stall single-player ticks and
 * the e2e harness), {@code closeOnEsc=true}, {@code returnTo='current'}.</p>
 *
 * @zh 一个脚本自建屏幕的声明式配置：标题、生命周期标志、有序平铺控件表与七个屏级回调字段。脚本向 {@code player.buildScreen} 传普通对象字面量；facade 解析成本 record、持有回调，并把无回调的中立 map 交给 adapter。
 */
@ApiClass(name = "ScreenSpec", kind = ApiClassKind.DATA)
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public record ScreenSpec(
        @ApiOptional @ApiType("string") String title,
        boolean showTitle,
        boolean pause,
        boolean closeOnEsc,
        @ApiType("string") String returnTo,
        @ApiType("ControlSpec[]") List<ControlSpec> controls,
        @ApiOptional @ApiType("(e: {pos: Pos2D, button: number,"
                + " modifiers: string[], delta?: {x: number, y: number},"
                + " doubleClick?: boolean}) => boolean") Consumer<Object[]> onMouseDown,
        @ApiOptional @ApiType("(e: {pos: Pos2D, button: number,"
                + " modifiers: string[], delta: {x: number, y: number}}) => boolean")
                Consumer<Object[]> onMouseDrag,
        @ApiOptional @ApiType("(e: {pos: Pos2D, button: number,"
                + " modifiers: string[], doubleClick?: boolean}) => boolean")
                Consumer<Object[]> onMouseUp,
        @ApiOptional @ApiType("(e: {key: string, code: number,"
                + " modifiers: string[]}) => boolean") Consumer<Object[]> onKeyPress,
        @ApiOptional @ApiType("(e: {char: string}) => boolean")
                Consumer<Object[]> onCharTyped,
        @ApiOptional @ApiType("(e: {ctx: ScreenCanvas, mouse: {x: number,"
                + " y: number}, partialTick: number}) => void")
                Consumer<Object[]> onRender,
        @ApiOptional @ApiType("() => void") Consumer<Object[]> onClose) {

    /** Screen-level callback names accepted by {@code gui.on(name, fn)}. */
    static final Set<String> CALLBACK_NAMES = Set.of(
            "mouseDown", "mouseDrag", "mouseUp", "keyPress", "charTyped",
            "render", "close");

    /** Spec object keys that hold screen-level callbacks. */
    private static final Map<String, String> CALLBACK_FIELDS = Map.of(
            "onMouseDown", "mouseDown",
            "onMouseDrag", "mouseDrag",
            "onMouseUp", "mouseUp",
            "onKeyPress", "keyPress",
            "onCharTyped", "charTyped",
            "onRender", "render",
            "onClose", "close");

    /**
     * Parses and validates one script spec literal.
     *
     * @throws IllegalArgumentException on a bad returnTo, a callback that is
     *         not a function, or an invalid/duplicate control
     */
    static ScreenSpec parse(Map<String, Object> raw) {
        String title = raw.get("title") instanceof String text ? text : "";
        boolean showTitle = boolOf(raw.get("showTitle"), true);
        boolean pause = boolOf(raw.get("pause"), false);
        boolean closeOnEsc = boolOf(raw.get("closeOnEsc"), true);
        String returnTo = raw.get("returnTo") instanceof String text ? text : "current";
        if (!"current".equals(returnTo) && !"game".equals(returnTo)) {
            throw new IllegalArgumentException(
                    "returnTo must be 'current' or 'game': " + returnTo);
        }

        List<ControlSpec> controls = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        Object controlsValue = raw.get("controls");
        if (controlsValue instanceof List<?> entries) {
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException(
                            "controls entries must be objects: " + entry);
                }
                ControlSpec control = ControlSpec.parse(castMap(map));
                if (!ids.add(control.id())) {
                    throw new IllegalArgumentException(
                            "duplicate control id: " + control.id());
                }
                controls.add(control);
            }
        } else if (controlsValue != null) {
            throw new IllegalArgumentException("controls must be an array");
        }

        return new ScreenSpec(title, showTitle, pause, closeOnEsc, returnTo,
                List.copyOf(controls),
                consumerOf(raw, "onMouseDown"),
                consumerOf(raw, "onMouseDrag"),
                consumerOf(raw, "onMouseUp"),
                consumerOf(raw, "onKeyPress"),
                consumerOf(raw, "onCharTyped"),
                consumerOf(raw, "onRender"),
                consumerOf(raw, "onClose"));
    }

    /** The callback-free neutral map handed to the screen port at open. */
    Map<String, Object> toNeutral() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", title != null ? title : "");
        map.put("showTitle", showTitle);
        map.put("pause", pause);
        map.put("closeOnEsc", closeOnEsc);
        map.put("returnTo", returnTo);
        List<Map<String, Object>> neutralControls = new ArrayList<>(controls.size());
        for (ControlSpec control : controls) {
            neutralControls.add(control.toNeutral());
        }
        map.put("controls", neutralControls);
        return map;
    }

    /** Resolves the spec callback for one {@code gui.on} name, or null. */
    Consumer<Object[]> callback(String name) {
        return switch (name) {
            case "mouseDown" -> onMouseDown;
            case "mouseDrag" -> onMouseDrag;
            case "mouseUp" -> onMouseUp;
            case "keyPress" -> onKeyPress;
            case "charTyped" -> onCharTyped;
            case "render" -> onRender;
            case "close" -> onClose;
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object[]> consumerOf(Map<String, Object> raw, String field) {
        Object value = raw.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Consumer<?> consumer) {
            return (Consumer<Object[]>) consumer;
        }
        throw new IllegalArgumentException(field + " must be a function");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static boolean boolOf(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }
}
