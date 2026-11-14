package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One declarative control in a {@link ScreenSpec}: frozen data, never a live
 * widget. A single discriminated record (the {@code DialogConfig} convention)
 * carries all seven control types — {@code button/imageButton/checkbox/
 * slider/editBox/cycleButton/lockButton} — with type-specific optional
 * fields; the adapter builds the vanilla widget from the neutral form.
 *
 * <p>Callbacks ({@code onClick}/{@code onChange}) never cross the port: the
 * facade unwraps them from the script object and holds them in the session.
 * Scripts pass a plain object literal; the engine hands the facade the
 * record's function fields as {@link Consumer} instances (the
 * {@code CommandSpec.handler} mechanism).</p>
 *
 * @zh {@link ScreenSpec} 中的一条声明式控件：冻结数据，不是活控件。单判别式 record 承载七类控件（button/imageButton/checkbox/slider/editBox/cycleButton/lockButton）的类型专属可选字段；adapter 由中立形态构造原版控件。回调（onClick/onChange）永不进端口。
 */
@ApiClass(name = "ControlSpec", kind = ApiClassKind.DATA)
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public record ControlSpec(
        @ApiType("string") String type,
        @ApiType("string") String id,
        int x,
        int y,
        int width,
        int height,
        int z,
        @ApiOptional @ApiType("string") String label,
        @ApiOptional @ApiType("boolean") Boolean checked,
        @ApiOptional @ApiType("number") Double value,
        @ApiOptional @ApiType("number") Integer steps,
        @ApiOptional @ApiType("string") String text,
        @ApiOptional @ApiType("number") Integer maxLength,
        @ApiOptional @ApiType("string") String suggestion,
        @ApiOptional @ApiType("boolean") Boolean editable,
        @ApiOptional @ApiType("boolean") Boolean showMessage,
        @ApiOptional @ApiType("string[]") String[] values,
        @ApiOptional @ApiType("string") String initial,
        @ApiOptional @ApiType("string[]") String[] alternatives,
        @ApiOptional @ApiType("string") String prefix,
        @ApiOptional @ApiType("boolean") Boolean locked,
        @ApiOptional @ApiType("boolean") Boolean active,
        @ApiOptional @ApiType("boolean") Boolean visible,
        @ApiOptional @ApiType("Record<string, string>") Map<String, String> sprites,
        @ApiOptional @ApiType("(e: {id: string, type: string}) => void")
                Consumer<Object[]> onClick,
        @ApiOptional @ApiType("(e: {id: string, type: string, checked?: boolean,"
                + " value?: number, text?: string, locked?: boolean}) => void")
                Consumer<Object[]> onChange) {

    /** Every legal control type discriminator. */
    static final Set<String> TYPES = Set.of(
            "button", "imageButton", "checkbox", "slider", "editBox",
            "cycleButton", "lockButton");

    /**
     * Keys a control of one type may carry beyond the common layout keys;
     * a key owned by another type is a spec error (field/type mismatch).
     */
    private static final Map<String, Set<String>> TYPE_KEYS = Map.of(
            "button", Set.of("label", "active", "visible", "onClick"),
            "imageButton", Set.of("sprites", "active", "visible", "onClick"),
            "checkbox", Set.of("label", "checked", "showMessage", "active",
                    "visible", "onChange"),
            "slider", Set.of("label", "value", "steps", "active", "visible",
                    "onChange"),
            "editBox", Set.of("label", "text", "maxLength", "suggestion",
                    "editable", "active", "visible", "onChange"),
            "cycleButton", Set.of("values", "initial", "alternatives", "prefix",
                    "active", "visible", "onChange"),
            "lockButton", Set.of("locked", "active", "visible", "onClick"));

    /** Common layout/status keys every control accepts. */
    private static final Set<String> COMMON_KEYS = Set.of(
            "type", "id", "x", "y", "width", "height", "z", "label", "active",
            "visible", "onClick", "onChange");

    /**
     * Parses and validates one script control literal.
     *
     * @throws IllegalArgumentException on unknown type, missing/duplicate
     *         id, or a field its type does not accept
     */
    static ControlSpec parse(Map<String, Object> raw) {
        Object typeValue = raw.get("type");
        String type = typeValue instanceof String text ? text : null;
        if (type == null || !TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "control type must be one of " + TYPES + ": " + typeValue);
        }
        Object idValue = raw.get("id");
        String id = idValue instanceof String text ? text : null;
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("control 'id' is required: " + type);
        }
        Set<String> allowed = TYPE_KEYS.get(type);
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            if (entry.getValue() == null) {
                // Records cross the engine boundary as full-component
                // snapshot maps (every component present, absent optionals
                // as null — G-036 build() round-trip); null reads as
                // "field not set" and skips the type whitelist check.
                continue;
            }
            if (!COMMON_KEYS.contains(key) && !allowed.contains(key)) {
                throw new IllegalArgumentException(
                        "control '" + id + "' of type " + type
                                + " does not accept field '" + key + "'");
            }
        }
        return new ControlSpec(
                type,
                id,
                intOf(raw.get("x"), 0),
                intOf(raw.get("y"), 0),
                intOf(raw.get("width"), 0),
                intOf(raw.get("height"), 0),
                intOf(raw.get("z"), 0),
                stringOf(raw.get("label")),
                boolOf(raw.get("checked")),
                doubleOf(raw.get("value")),
                intObjOf(raw.get("steps")),
                stringOf(raw.get("text")),
                intObjOf(raw.get("maxLength")),
                stringOf(raw.get("suggestion")),
                boolOf(raw.get("editable")),
                boolOf(raw.get("showMessage")),
                stringsOf(raw.get("values")),
                stringOf(raw.get("initial")),
                stringsOf(raw.get("alternatives")),
                stringOf(raw.get("prefix")),
                boolOf(raw.get("locked")),
                boolOf(raw.get("active")),
                boolOf(raw.get("visible")),
                spritesOf(raw.get("sprites"), id),
                consumerOf(raw.get("onClick"), id, "onClick"),
                consumerOf(raw.get("onChange"), id, "onChange"));
    }

    /**
     * The neutral map handed to {@link ink.tuanzi.neomacros.port.ScreenPort}
     * (callbacks stripped; absent optionals stay absent).
     */
    Map<String, Object> toNeutral() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("id", id);
        map.put("x", x);
        map.put("y", y);
        map.put("width", width);
        map.put("height", height);
        map.put("z", z);
        putIfPresent(map, "label", label);
        putIfPresent(map, "checked", checked);
        putIfPresent(map, "value", value);
        putIfPresent(map, "steps", steps);
        putIfPresent(map, "text", text);
        putIfPresent(map, "maxLength", maxLength);
        putIfPresent(map, "suggestion", suggestion);
        putIfPresent(map, "editable", editable);
        putIfPresent(map, "showMessage", showMessage);
        if (values != null) {
            map.put("values", java.util.List.of(values));
        }
        putIfPresent(map, "initial", initial);
        if (alternatives != null) {
            map.put("alternatives", java.util.List.of(alternatives));
        }
        putIfPresent(map, "prefix", prefix);
        putIfPresent(map, "locked", locked);
        if (active != null) {
            map.put("active", active);
        }
        if (visible != null) {
            map.put("visible", visible);
        }
        if (sprites != null) {
            map.put("sprites", new LinkedHashMap<>(sprites));
        }
        return map;
    }

    /** The initial state snapshot (cache seed before any widget exists). */
    ControlState toState() {
        return new ControlState(id, type, x, y, width, height, z,
                label != null ? label : "",
                active == null || active,
                visible == null || visible,
                checked, value, text, locked,
                steps, editable, maxLength, null);
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static int intOf(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Integer intObjOf(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Double doubleOf(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static Boolean boolOf(Object value) {
        return value instanceof Boolean flag ? flag : null;
    }

    private static String stringOf(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String[] stringsOf(Object value) {
        if (value instanceof String[] texts) {
            return texts;
        }
        if (value instanceof java.util.List<?> list) {
            String[] out = new String[list.size()];
            for (int i = 0; i < out.length; i++) {
                Object element = list.get(i);
                if (!(element instanceof String text)) {
                    throw new IllegalArgumentException(
                            "control option arrays must hold strings: " + value);
                }
                out[i] = text;
            }
            return out;
        }
        if (value == null) {
            return null;
        }
        throw new IllegalArgumentException(
                "control option arrays must be string arrays: " + value);
    }

    private static Map<String, String> spritesOf(Object value, String id) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException(
                    "control '" + id + "' sprites must be an object: " + value);
        }
        Map<String, String> sprites = new LinkedHashMap<>();
        for (String required : new String[]{
                "enabled", "disabled", "enabledFocused", "disabledFocused"}) {
            Object entry = raw.get(required);
            if (!(entry instanceof String texture)) {
                throw new IllegalArgumentException("control '" + id
                        + "' sprites requires string field '" + required + "'");
            }
            sprites.put(required, texture);
        }
        return sprites;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object[]> consumerOf(Object value, String id, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Consumer<?> consumer) {
            return (Consumer<Object[]>) consumer;
        }
        throw new IllegalArgumentException(
                "control '" + id + "' " + field + " must be a function");
    }
}
