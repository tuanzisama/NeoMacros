package ink.tuanzi.neomacros.fabric.web.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Deterministic JSON mapping between typed values (records, lists, maps,
 * primitives, enums) and the Gson tree model. Records serialize in component
 * declaration order with explicit nulls; {@link #stringify} can additionally
 * sort object keys, which keeps generated contract documents byte-stable.
 */
public final class Jsons {

    private Jsons() {
    }

    /** Maps any supported value into the Gson tree model. */
    public static JsonElement valueOf(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof JsonElement element) {
            return element;
        }
        if (value instanceof String s) {
            return new JsonPrimitive(s);
        }
        if (value instanceof Number n) {
            return new JsonPrimitive(n);
        }
        if (value instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        if (value instanceof Character c) {
            return new JsonPrimitive(c.toString());
        }
        if (value instanceof java.util.UUID uuid) {
            return new JsonPrimitive(uuid.toString());
        }
        if (value instanceof Enum<?> e) {
            return new JsonPrimitive(e.name());
        }
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray(list.size());
            for (Object item : list) {
                array.add(valueOf(item));
            }
            return array;
        }
        if (value instanceof Object[] array) {
            JsonArray out = new JsonArray(array.length);
            for (Object item : array) {
                out.add(valueOf(item));
            }
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                object.add(String.valueOf(entry.getKey()), valueOf(entry.getValue()));
            }
            return object;
        }
        if (value instanceof Record record) {
            JsonObject object = new JsonObject();
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                Object item;
                try {
                    item = component.getAccessor().invoke(record);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("cannot read component "
                            + component.getName(), e);
                }
                object.add(component.getName(), valueOf(item));
            }
            return object;
        }
        throw new IllegalArgumentException("unsupported JSON value: " + value.getClass());
    }

    /** Serializes a value compactly, preserving record declaration order. */
    public static String stringify(Object value) {
        return stringify(value, false);
    }

    /**
     * Serializes a value; when {@code sortKeys} is set, every object's keys
     * are sorted recursively so output is stable across runs.
     */
    public static String stringify(Object value, boolean sortKeys) {
        JsonElement element = value instanceof JsonElement json ? json : valueOf(value);
        if (sortKeys) {
            element = deepSort(element);
        }
        return element.toString();
    }

    /** Parses a JSON document, or throws {@code IllegalArgumentException}. */
    public static JsonElement parse(String text) {
        try {
            return JsonParser.parseString(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a JSON element into plain Java values (JsonObject → ordered
     * Map, JsonArray → List, primitives → String/Number/Boolean, null → null).
     */
    public static Object plain(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                out.put(entry.getKey(), plain(entry.getValue()));
            }
            return out;
        }
        if (element.isJsonArray()) {
            List<Object> out = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                out.add(plain(item));
            }
            return out;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsNumber();
        }
        return primitive.getAsString();
    }

    /**
     * Decodes a JSON object into an instance of a record type. Components
     * missing from the JSON become null (the handler validates); values are
     * coerced to the component type (String, numeric, boolean, List, Map,
     * nested record, or the raw JsonElement). Unknown keys are ignored.
     */
    public static <T> T recordOf(JsonElement element, Class<T> type) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException("body type must be a record: " + type.getName());
        }
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("expected a JSON object body");
        }
        JsonObject object = element.getAsJsonObject();
        RecordComponent[] components = type.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            args[i] = componentValue(object.get(components[i].getName()), components[i].getType());
        }
        try {
            var constructor = type.getDeclaredConstructor(
                    java.util.Arrays.stream(components).map(RecordComponent::getType)
                            .toArray(Class<?>[]::new));
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("cannot build " + type.getSimpleName()
                    + ": " + e.getMessage(), e);
        }
    }

    private static Object componentValue(JsonElement element, Class<?> type) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (type == String.class) {
            return element.getAsString();
        }
        if (type == int.class || type == Integer.class) {
            return element.getAsInt();
        }
        if (type == long.class || type == Long.class) {
            return element.getAsLong();
        }
        if (type == boolean.class || type == Boolean.class) {
            return element.getAsBoolean();
        }
        if (type == double.class || type == Double.class) {
            return element.getAsDouble();
        }
        if (type == JsonElement.class) {
            return element;
        }
        if (type == Map.class) {
            return plain(element);
        }
        if (type == List.class) {
            return plain(element);
        }
        if (type.isRecord()) {
            return recordOf(element, type);
        }
        throw new IllegalArgumentException("unsupported component type: " + type.getName());
    }

    /** Converts a JSON object into an ordered String→value map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> objectOf(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        return (Map<String, Object>) plain(element);
    }

    /** Returns a copy of the element with every object's keys sorted. */
    private static JsonElement deepSort(JsonElement element) {
        if (element instanceof JsonObject object) {
            List<String> keys = new ArrayList<>(object.keySet());
            keys.sort(Comparator.naturalOrder());
            JsonObject sorted = new JsonObject();
            for (String key : keys) {
                sorted.add(key, deepSort(object.get(key)));
            }
            return sorted;
        }
        if (element instanceof JsonArray array) {
            JsonArray out = new JsonArray(array.size());
            for (JsonElement item : array) {
                out.add(deepSort(item));
            }
            return out;
        }
        return element;
    }
}
