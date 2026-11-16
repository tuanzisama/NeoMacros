package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.console.ScriptConsole;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders guest values as the neutral console node model shared with the
 * editor (see the ConsoleEntry contract): scalars cross directly, objects
 * and arrays are enumerated with previews up to the contract caps, guest
 * Errors get a dedicated node, and host values resolve through the
 * installed product component view (MC rich text), then Class/Throwable
 * shortcuts, then a generic reflection summary. All work runs on the
 * owning context's loop thread; the node budget bounds worst-case cost per
 * emission so a pathological argument tree cannot stall the loop.
 */
final class ConsoleFormatter {

    /** Caps shared with the SSE/console contract. */
    static final int MAX_STRING = 2000;
    static final int MAX_PREVIEW = 140;
    static final int MAX_PROPS = 30;
    static final int MAX_ITEMS = 100;
    static final int MAX_ARGS = 64;
    static final int MAX_DEPTH = 4;
    static final int MAX_NODES = 400;
    static final int MAX_HOST_PROPS = 15;
    static final int MAX_STACK = 4000;

    private final Context context;
    private final AtomicInteger budget;
    private final boolean fullLength;

    /** Creates the formatter; {@code nodeBudget} bounds this emission. */
    ConsoleFormatter(Context context, int nodeBudget) {
        this(context, nodeBudget, false);
    }

    /**
     * Creates the formatter; {@code fullLength} lifts the string and stack
     * length caps (the DevTools eval result must reach the editor intact —
     * oversized payloads ride the SSE segment frames instead), while the
     * structural caps (items/props/depth/nodes) stay to bound loop work.
     */
    ConsoleFormatter(Context context, int nodeBudget, boolean fullLength) {
        this.context = context;
        this.budget = new AtomicInteger(nodeBudget);
        this.fullLength = fullLength;
    }

    /** Renders one root-level value as a node map. */
    Map<String, Object> node(Value value) {
        return node(value, 0);
    }

    /** Caps a stack capture to the contract length. */
    static String capStack(String stack) {
        return cap(stack, MAX_STACK);
    }

    /**
     * Applies a contract length cap unless this formatter runs full-length
     * (eval results deliver strings and stacks intact).
     */
    private String capResult(String text, int limit) {
        return fullLength ? (text == null ? "" : text) : cap(text, limit);
    }

    /** One-line display text for a node (previews inside obj/arr nodes). */
    static String previewOf(Map<String, Object> node) {
        String type = String.valueOf(node.get("t"));
        return switch (type) {
            case "str" -> "'" + node.get("v") + "'";
            case "num", "bool" -> String.valueOf(node.get("v"));
            case "null" -> "null";
            case "undef" -> "undefined";
            case "fn" -> "ƒ " + node.getOrDefault("name", "") + "()";
            case "arr" -> "Array(" + node.get("len") + ")";
            case "obj" -> "{" + node.getOrDefault("pre", "") + "}";
            case "err" -> node.get("name") + ": " + node.get("msg");
            case "class" -> "class " + node.get("name");
            case "comp" -> "Component('" + cap(flatText(node.get("rich")), MAX_PREVIEW) + "')";
            case "host" -> String.valueOf(node.get("cls"));
            default -> String.valueOf(node.get("v"));
        };
    }

    private Map<String, Object> node(Value value, int depth) {
        if (budget.decrementAndGet() < 0) {
            return stringNode("…");
        }
        if (value == null || value.isNull()) {
            return undefinedOrNull(value);
        }
        if (value.isString()) {
            return stringNode(capResult(value.asString(), MAX_STRING));
        }
        if (value.isBoolean()) {
            Map<String, Object> out = tagged("bool");
            out.put("v", value.asBoolean());
            return out;
        }
        if (value.isNumber()) {
            return numberNode(value);
        }
        if (value.isHostObject()) {
            return hostNode(value.asHostObject(), depth);
        }
        if (value.canExecute()) {
            Map<String, Object> out = tagged("fn");
            out.put("name", safeFunctionName(value));
            return out;
        }
        if (depth >= MAX_DEPTH) {
            return stringNode(cap(summarize(value), MAX_PREVIEW));
        }
        // Glue-class handles (neo.X instances) keep every member on the
        // prototype behind a hidden non-enumerable __cell: own-key
        // enumeration sees nothing and the node renders as an empty {}.
        // Render the member proxy instead — it enumerates the handle's
        // live surface (properties read per access, methods as functions).
        Value cell = handleCell(value);
        if (cell != null) {
            return node(cell, depth);
        }
        // Interop proxies (JavaObject/JavaClass) enumerate only
        // className/instanceOf as keys; the wrapped value's real surface
        // is reachable but not discoverable. Render it instead — public
        // fields as live values, methods as functions, arrays as arrays.
        Map<String, Object> interop = interopNode(value, depth);
        if (interop != null) {
            return interop;
        }
        if (looksLikeError(value)) {
            return errorNode(value);
        }
        if (value.hasArrayElements()) {
            return arrayNode(value, depth);
        }
        if (value.hasMembers()) {
            return objectNode(value, depth);
        }
        return stringNode(capResult(safeToString(value), MAX_STRING));
    }

    /**
     * The member proxy a glue-class handle hides under its non-enumerable
     * {@code __cell} own property, or null for anything that is not one.
     */
    private static Value handleCell(Value value) {
        try {
            if (value.hasMember("__cell")) {
                Value cell = value.getMember("__cell");
                if (cell != null && !cell.isNull()) {
                    return cell;
                }
            }
        } catch (RuntimeException ignored) {
            // plain guest values without the probe fall through
        }
        return null;
    }

    /**
     * The member surface of a JavaObject/JavaClass interop proxy: public
     * fields as live values and methods as functions, mirroring what the
     * proxy itself dispatches (instance members, or statics plus the
     * {@code new} constructor member for a class). A wrapped array renders
     * as an array. Returns null when the proxy carries no raw value so the
     * caller falls back to the plain className/instanceOf key view.
     */
    private Map<String, Object> interopNode(Value value, int depth) {
        Object raw;
        try {
            if (!value.hasMember("__neoRaw")) {
                return null;
            }
            Value rawValue = value.getMember("__neoRaw");
            if (rawValue == null || rawValue.isNull() || !rawValue.isHostObject()) {
                return null;
            }
            raw = rawValue.asHostObject();
        } catch (RuntimeException e) {
            return null;
        }
        if (raw == null) {
            return null;
        }
        if (raw.getClass().isArray()) {
            return arrayNodeFor(raw, depth);
        }
        boolean classView = raw instanceof Class<?>;
        Class<?> surface = classView ? (Class<?>) raw : raw.getClass();
        boolean statik = classView;
        Map<String, Map<String, Object>> members = new LinkedHashMap<>();
        members.put("className", stringNode(classView
                ? ((Class<?>) raw).getName() : raw.getClass().getName()));
        members.put("instanceOf", fnNode(""));
        if (classView) {
            // Head the class surface with construction so the property cap
            // never cuts the member scripts reach for first.
            members.put("new", fnNode(""));
        }
        try {
            for (Field field : surface.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) != statik) {
                    continue;
                }
                final Field member = field;
                Object holder = classView ? null : raw;
                members.putIfAbsent(field.getName(), childNode(() -> {
                    try {
                        return context.asValue(member.get(holder));
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        throw new IllegalStateException(e);
                    }
                }, depth + 1));
            }
        } catch (RuntimeException ignored) {
            // an unreadable surface keeps the keys it managed to list
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Method method : surface.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) != statik) {
                continue;
            }
            if (seen.add(method.getName())) {
                members.put(method.getName(), fnNode(""));
            }
        }
        return surfaceNode(members);
    }

    /**
     * The array-node contract shape built straight from a host array:
     * {@code context.asValue} does not expose array elements under the
     * closed host-access policy, so the generic converter cannot.
     */
    private Map<String, Object> arrayNodeFor(Object array, int depth) {
        Map<String, Object> out = tagged("arr");
        long size = java.lang.reflect.Array.getLength(array);
        long shown = Math.min(size, MAX_ITEMS);
        List<Object> items = new ArrayList<>();
        List<String> previews = new ArrayList<>();
        for (long i = 0; i < shown; i++) {
            final int index = (int) i;
            Map<String, Object> child = childNode(() -> {
                Object element = java.lang.reflect.Array.get(array, index);
                return element == null ? null : context.asValue(element);
            }, depth + 1);
            items.add(child);
            previews.add(previewOf(child));
        }
        out.put("len", size);
        out.put("items", items);
        out.put("pre", joinPreview(previews, size > shown));
        out.put("tr", size > shown);
        return out;
    }

    /**
     * Caps a discovered member map into the obj-node contract shape
     * (pre/props/n/tr) so richer surfaces render like any other object.
     */
    private Map<String, Object> surfaceNode(Map<String, Map<String, Object>> members) {
        Map<String, Object> out = tagged("obj");
        int total = members.size();
        List<Object> props = new ArrayList<>();
        List<String> previews = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, Map<String, Object>> entry : members.entrySet()) {
            if (shown >= MAX_PROPS || budget.decrementAndGet() < 0) {
                break;
            }
            props.add(prop(entry.getKey(), entry.getValue()));
            previews.add(entry.getKey() + ": " + previewOf(entry.getValue()));
            shown++;
        }
        out.put("pre", joinPreview(previews, total > shown));
        out.put("props", props);
        out.put("n", (long) total);
        out.put("tr", total > shown);
        return out;
    }

    /** One display-only function node carrying the member's name. */
    private static Map<String, Object> fnNode(String name) {
        Map<String, Object> out = tagged("fn");
        out.put("name", name);
        return out;
    }

    /** Distinguishes guest {@code undefined} from {@code null} (both read isNull). */
    private Map<String, Object> undefinedOrNull(Value value) {
        if (value != null && "undefined".equals(safeToString(value))) {
            return tagged("undef");
        }
        return tagged("null");
    }

    private Map<String, Object> numberNode(Value value) {
        Map<String, Object> out = tagged("num");
        if (value.fitsInInt()) {
            out.put("v", value.asInt());
        } else if (value.fitsInLong()) {
            out.put("v", value.asLong());
        } else {
            double d = value.asDouble();
            if (Double.isNaN(d)) {
                out.put("v", "NaN");
            } else if (Double.isInfinite(d)) {
                out.put("v", d > 0 ? "Infinity" : "-Infinity");
            } else {
                out.put("v", d);
            }
        }
        return out;
    }

    /** Guest Errors are plain objects carrying name/message/stack members. */
    private static boolean looksLikeError(Value value) {
        try {
            return value.hasMembers() && value.hasMember("name")
                    && value.hasMember("message") && value.hasMember("stack");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Map<String, Object> errorNode(Value value) {
        Map<String, Object> out = tagged("err");
        out.put("name", stringMember(value, "name", "Error"));
        out.put("msg", stringMember(value, "message", ""));
        out.put("stack", capResult(stringMember(value, "stack", ""), MAX_STACK));
        return out;
    }

    private Map<String, Object> arrayNode(Value value, int depth) {
        Map<String, Object> out = tagged("arr");
        long size = Math.max(0, value.getArraySize());
        long shown = Math.min(size, MAX_ITEMS);
        List<Object> items = new ArrayList<>();
        List<String> previews = new ArrayList<>();
        for (long i = 0; i < shown; i++) {
            long index = i;
            Map<String, Object> child = childNode(() -> value.getArrayElement(index), depth + 1);
            items.add(child);
            previews.add(previewOf(child));
        }
        boolean truncated = size > shown;
        out.put("len", size);
        out.put("items", items);
        out.put("pre", joinPreview(previews, truncated));
        out.put("tr", truncated);
        return out;
    }

    private Map<String, Object> objectNode(Value value, int depth) {
        Map<String, Object> out = tagged("obj");
        List<String> keys;
        try {
            keys = new ArrayList<>(value.getMemberKeys());
        } catch (RuntimeException e) {
            keys = List.of();
        }
        int shown = Math.min(keys.size(), MAX_PROPS);
        List<Object> props = new ArrayList<>();
        List<String> previews = new ArrayList<>();
        for (int i = 0; i < shown; i++) {
            String key = keys.get(i);
            Map<String, Object> child = childNode(() -> value.getMember(key), depth + 1);
            props.add(prop(key, child));
            previews.add(key + ": " + previewOf(child));
        }
        boolean truncated = keys.size() > shown;
        out.put("pre", joinPreview(previews, truncated));
        out.put("props", props);
        out.put("n", (long) keys.size());
        out.put("tr", truncated);
        return out;
    }

    /** Reads one member through a supplier so failures render as unavailable. */
    private Map<String, Object> childNode(java.util.function.Supplier<Value> read, int depth) {
        try {
            return node(read.get(), depth);
        } catch (RuntimeException e) {
            return stringNode("<unavailable>");
        }
    }

    private Map<String, Object> hostNode(Object host, int depth) {
        ChatComponent rich = ScriptConsole.componentView(host);
        if (rich != null) {
            Map<String, Object> out = tagged("comp");
            out.put("rich", richMap(rich));
            return out;
        }
        if (host instanceof Class<?> cls) {
            Map<String, Object> out = tagged("class");
            out.put("name", cls.getName());
            return out;
        }
        if (host instanceof Throwable failure) {
            Map<String, Object> out = tagged("err");
            out.put("name", failure.getClass().getName());
            out.put("msg", failure.getMessage() == null ? "" : failure.getMessage());
            out.put("stack", capResult(toStringOf(failure), MAX_STACK));
            return out;
        }
        Map<String, Object> out = tagged("host");
        out.put("cls", host.getClass().getName());
        out.put("pre", cap(toStringOf(host), MAX_PREVIEW));
        if (depth < MAX_DEPTH) {
            List<Object> props = new ArrayList<>();
            Set<String> keys = new LinkedHashSet<>();
            for (Map.Entry<String, Object> entry : hostProps(host).entrySet()) {
                Map<String, Object> child;
                try {
                    child = node(context.asValue(entry.getValue()), depth + 1);
                } catch (RuntimeException e) {
                    child = stringNode("<unavailable>");
                }
                props.add(prop(entry.getKey(), child));
                keys.add(entry.getKey());
            }
            // Method names as display-only functions while room remains:
            // the console should show what the object can do, not only
            // what its getters currently read.
            for (Method method : host.getClass().getMethods()) {
                if (props.size() >= MAX_HOST_PROPS) {
                    break;
                }
                if (method.getDeclaringClass() == Object.class
                        || !Modifier.isPublic(method.getModifiers())
                        || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                String name = method.getName();
                boolean getter = (name.startsWith("get") && name.length() > 3)
                        || (name.startsWith("is") && name.length() > 2);
                if (name.equals("getClass")
                        || (getter && method.getParameterCount() == 0)) {
                    continue; // surfaced as a live value above (or excluded there)
                }
                if (keys.add(name)) {
                    props.add(prop(name, fnNode("")));
                }
            }
            if (!props.isEmpty()) {
                out.put("props", props);
            }
        }
        return out;
    }

    /** Public no-argument getter snapshot (bounded) for generic host values. */
    private Map<String, Object> hostProps(Object host) {
        Map<String, Object> out = new LinkedHashMap<>();
        int used = 0;
        for (Method method : host.getClass().getMethods()) {
            if (used >= MAX_HOST_PROPS) {
                break;
            }
            if (method.getParameterCount() != 0
                    || method.getDeclaringClass() == Object.class
                    || !Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            boolean getter = (name.startsWith("get") && name.length() > 3)
                    || (name.startsWith("is") && name.length() > 2);
            if (!getter || name.equals("getClass")) {
                continue;
            }
            try {
                Object value = method.invoke(host);
                out.put(decapitalize(name.startsWith("is") ? name.substring(2) : name.substring(3)), value);
                used++;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // skip unreadable getters
            }
        }
        return out;
    }

    /** Neutral rich-text map of one ChatComponent (nulls omitted). */
    private static Map<String, Object> richMap(ChatComponent rich) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("text", rich.text());
        putIfNotNull(out, "color", rich.color());
        putIfNotNull(out, "bold", rich.bold());
        putIfNotNull(out, "italic", rich.italic());
        putIfNotNull(out, "underlined", rich.underlined());
        putIfNotNull(out, "strikethrough", rich.strikethrough());
        putIfNotNull(out, "obfuscated", rich.obfuscated());
        if (!rich.children().isEmpty()) {
            List<Object> children = new ArrayList<>();
            for (ChatComponent child : rich.children()) {
                children.add(richMap(child));
            }
            out.put("children", children);
        }
        return out;
    }

    /** Flattened text of a rich map (preview use). */
    private static String flatText(Object rich) {
        if (!(rich instanceof Map<?, ?> map)) {
            return "";
        }
        Object text = map.get("text");
        StringBuilder out = new StringBuilder(text == null ? "" : String.valueOf(text));
        Object children = map.get("children");
        if (children instanceof List<?> list) {
            for (Object child : list) {
                out.append(flatText(child));
            }
        }
        return out.toString();
    }

    private static String summarize(Value value) {
        try {
            if (value.canExecute()) {
                return "ƒ";
            }
            if (value.hasArrayElements()) {
                return "Array(" + value.getArraySize() + ")";
            }
            if (looksLikeError(value)) {
                return stringMember(value, "name", "Error");
            }
            if (value.hasMembers()) {
                return "{…}";
            }
            return safeToString(value);
        } catch (RuntimeException e) {
            return "<unavailable>";
        }
    }

    private static String safeToString(Value value) {
        try {
            String text = value.toString();
            return text == null ? "null" : text;
        } catch (RuntimeException e) {
            return "<unavailable>";
        }
    }

    private static String toStringOf(Object host) {
        try {
            String text = String.valueOf(host);
            return text == null ? "null" : text;
        } catch (RuntimeException e) {
            return "<unavailable>";
        }
    }

    private static String safeFunctionName(Value value) {
        try {
            Value name = value.getMember("name");
            if (name != null && name.isString()) {
                return name.asString();
            }
        } catch (RuntimeException ignored) {
            // fall through to the empty name
        }
        return "";
    }

    private static String stringMember(Value value, String name, String fallback) {
        try {
            Value member = value.getMember(name);
            if (member != null && !member.isNull() && member.isString()) {
                return member.asString();
            }
        } catch (RuntimeException ignored) {
            // fall through to the fallback
        }
        return fallback;
    }

    private static String joinPreview(List<String> parts, boolean truncated) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (out.length() > 0) {
                out.append(", ");
            }
            if (out.length() + part.length() > MAX_PREVIEW) {
                out.append("…");
                return out.toString();
            }
            out.append(part);
        }
        if (truncated) {
            out.append(", …");
        }
        return out.toString();
    }

    private static Map<String, Object> tagged(String type) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("t", type);
        return out;
    }

    private static Map<String, Object> stringNode(String text) {
        Map<String, Object> out = tagged("str");
        out.put("v", text);
        return out;
    }

    private static Map<String, Object> prop(String key, Map<String, Object> value) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("k", key);
        out.put("v", value);
        return out;
    }

    private static void putIfNotNull(Map<String, Object> out, String key, Object value) {
        if (value != null) {
            out.put(key, value);
        }
    }

    private static String cap(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit) + "…";
    }

    private static String decapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
