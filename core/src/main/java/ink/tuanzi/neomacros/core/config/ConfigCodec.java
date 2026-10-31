package ink.tuanzi.neomacros.core.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reflection codec between {@link Option}-annotated section fields and JSON.
 * Supported field types: boolean, int, long, double, String,
 * {@code List<String>}, and {@code Map<String, List<String>>}. Numeric values
 * are clamped to the declared {@link Option#min()}/{@link Option#max()}.
 */
final class ConfigCodec {

    private ConfigCodec() {
    }

    /** Writes every annotated field of {@code section} into a JSON object. */
    static JsonObject save(Object section) {
        JsonObject out = new JsonObject();
        for (Field field : annotatedFields(section.getClass())) {
            Object value;
            try {
                value = field.get(section);
            } catch (IllegalAccessException e) {
                continue;
            }
            out.add(keyOf(field), toJson(value));
        }
        return out;
    }

    /** Reads JSON values into the annotated fields; missing keys keep defaults. */
    static void load(Object section, JsonObject data, Consumer<String> warn) {
        var knownKeys = new java.util.LinkedHashSet<String>();
        for (Field field : annotatedFields(section.getClass())) {
            String key = keyOf(field);
            knownKeys.add(key);
            JsonElement element = data.get(key);
            if (element == null) {
                continue;
            }
            try {
                field.set(section, fromJson(field, element));
            } catch (IllegalArgumentException | ClassCastException e) {
                warn.accept("option '" + key + "' has an incompatible value; keeping default");
            } catch (IllegalAccessException e) {
                warn.accept("option '" + key + "' is not writable; keeping default");
            }
        }
        for (String dataKey : data.keySet()) {
            if (!knownKeys.contains(dataKey)) {
                warn.accept("unknown option '" + dataKey + "' ignored");
            }
        }
    }

    private static List<Field> annotatedFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Option.class)) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static String keyOf(Field field) {
        String name = field.getAnnotation(Option.class).name();
        return name.isEmpty() ? field.getName() : name;
    }

    private static Object fromJson(Field field, JsonElement element) {
        Class<?> type = field.getType();
        Option option = field.getAnnotation(Option.class);
        if (type == boolean.class) {
            return element.getAsBoolean();
        }
        if (type == int.class) {
            return clamp(element.getAsInt(), option);
        }
        if (type == long.class) {
            return clamp(element.getAsLong(), option);
        }
        if (type == double.class) {
            return clamp(element.getAsDouble(), option);
        }
        if (type == String.class) {
            return element.getAsString();
        }
        if (type == List.class) {
            List<String> list = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                list.add(item.getAsString());
            }
            return list;
        }
        if (type == Map.class) {
            Map<String, List<String>> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                List<String> list = new ArrayList<>();
                for (JsonElement item : entry.getValue().getAsJsonArray()) {
                    list.add(item.getAsString());
                }
                map.put(entry.getKey(), list);
            }
            return map;
        }
        throw new IllegalArgumentException("unsupported option type: " + type);
    }

    private static JsonElement toJson(Object value) {
        if (value instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        if (value instanceof Integer i) {
            return new JsonPrimitive(i);
        }
        if (value instanceof Long l) {
            return new JsonPrimitive(l);
        }
        if (value instanceof Double d) {
            return new JsonPrimitive(d);
        }
        if (value instanceof String str) {
            return new JsonPrimitive(str);
        }
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            for (Object item : list) {
                array.add(new JsonPrimitive(String.valueOf(item)));
            }
            return array;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                JsonArray array = new JsonArray();
                for (Object item : (List<?>) entry.getValue()) {
                    array.add(new JsonPrimitive(String.valueOf(item)));
                }
                object.add(String.valueOf(entry.getKey()), array);
            }
            return object;
        }
        throw new IllegalArgumentException("unsupported option value: " + value.getClass());
    }

    private static int clamp(int value, Option option) {
        return (int) clamp((double) value, option);
    }

    private static long clamp(long value, Option option) {
        return (long) clamp((double) value, option);
    }

    private static double clamp(double value, Option option) {
        double min = option.min();
        double max = option.max();
        if (min != Option.NO_BOUND && value < min) {
            return min;
        }
        if (max != Option.NO_BOUND && value > max) {
            return max;
        }
        return value;
    }
}
