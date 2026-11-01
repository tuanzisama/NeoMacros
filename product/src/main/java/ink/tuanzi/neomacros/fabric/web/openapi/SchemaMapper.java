package ink.tuanzi.neomacros.fabric.web.openapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import ink.tuanzi.neomacros.fabric.web.api.NmNullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Java types onto OpenAPI 3.0 JSON Schemas. Records become referenced
 * component schemas (required = components without {@code @NmNullable},
 * descriptions from {@code @NmDesc}); enums become string enums; collections
 * and maps decompose into arrays and additional-properties objects.
 */
final class SchemaMapper {

    /** The schema components collected while mapping (name → schema). */
    private final Map<String, Class<?>> pending = new LinkedHashMap<>();
    private final Map<String, JsonObject> schemas = new LinkedHashMap<>();

    /** Returns the collected component schemas after a mapping pass. */
    Map<String, JsonObject> collected() {
        // recordSchema can register further nested records; iterate until
        // the pending set stops growing.
        while (true) {
            List<String> toDo = pending.keySet().stream()
                    .filter(name -> !schemas.containsKey(name))
                    .toList();
            if (toDo.isEmpty()) {
                break;
            }
            for (String name : toDo) {
                if (!schemas.containsKey(name)) {
                    schemas.put(name, recordSchema(pending.get(name)));
                }
            }
        }
        return schemas;
    }

    /** The schema (or reference) for the given type; registers records. */
    JsonObject schemaOf(Type type) {
        JsonObject schema = inlineSchema(type);
        return schema != null ? schema : referenceOf(type);
    }

    /** Inline schemas: primitives, enums, collections, maps, any. */
    @SuppressWarnings("unchecked")
    private JsonObject inlineSchema(Type type) {
        if (type == String.class) {
            return object("type", "string");
        }
        if (type == int.class || type == Integer.class) {
            return object("type", "integer");
        }
        if (type == long.class || type == Long.class) {
            JsonObject schema = object("type", "integer");
            schema.addProperty("format", "int64");
            return schema;
        }
        if (type == boolean.class || type == Boolean.class) {
            return object("type", "boolean");
        }
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            return object("type", "number");
        }
        if (type instanceof Class<?> clazz && clazz.isEnum()) {
            JsonObject schema = object("type", "string");
            var values = new JsonArray();
            for (Object constant : clazz.getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
            schema.add("enum", values);
            return schema;
        }
        if (type instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw == List.class || raw == java.util.Collection.class) {
                JsonObject schema = object("type", "array");
                schema.add("items", schemaOf(parameterized.getActualTypeArguments()[0]));
                return schema;
            }
            if (raw == Map.class) {
                JsonObject schema = object("type", "object");
                schema.add("additionalProperties",
                        schemaOf(parameterized.getActualTypeArguments()[1]));
                return schema;
            }
            return null;
        }
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                JsonObject schema = object("type", "array");
                schema.add("items", schemaOf(clazz.getComponentType()));
                return schema;
            }
            if (clazz == Object.class || clazz == com.google.gson.JsonElement.class
                    || clazz == void.class || clazz == Void.class) {
                return new JsonObject();
            }
            return null;
        }
        return null;
    }

    /** Registers (or reuses) a record component schema and returns its ref. */
    private JsonObject referenceOf(Type type) {
        if (!(type instanceof Class<?> clazz) || !clazz.isRecord()) {
            throw new IllegalArgumentException("unsupported schema type: " + type);
        }
        String name = clazz.getSimpleName();
        pending.putIfAbsent(name, clazz);
        JsonObject ref = new JsonObject();
        ref.addProperty("$ref", "#/components/schemas/" + name);
        return ref;
    }

    private JsonObject recordSchema(Class<?> record) {
        JsonObject schema = object("type", "object");
        var required = new JsonArray();
        var properties = new JsonObject();
        for (RecordComponent component : record.getRecordComponents()) {
            JsonObject property = schemaOf(component.getGenericType());
            NmDesc desc = component.getAnnotation(NmDesc.class);
            if (desc != null) {
                property.addProperty("description", desc.value());
            }
            if (component.getAnnotation(NmNullable.class) != null) {
                property.addProperty("nullable", true);
            } else {
                required.add(component.getName());
            }
            properties.add(component.getName(), property);
        }
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    private static JsonObject object(String key, String value) {
        JsonObject out = new JsonObject();
        out.addProperty(key, value);
        return out;
    }
}
