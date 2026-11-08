package ink.tuanzi.neomacros.fabric.mcp.tools;

import com.google.gson.JsonObject;
import ink.tuanzi.neomacros.fabric.mcp.McpToolError;

import java.util.List;
import java.util.Map;

/**
 * Schema and argument helpers for the MCP tools: every inputSchema is a
 * draft-2020-12 object built from these primitives, and the handlers decode
 * the (weakly typed) JSON arguments through the {@code arg*} readers, which
 * raise {@link McpToolError} with an agent-readable message on type
 * mismatches.
 */
public final class McpSchemas {

    private McpSchemas() {
    }

    /** An object schema with the given properties and required names. */
    public static JsonObject objectSchema(JsonObject properties, String... required) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        if (required.length > 0) {
            com.google.gson.JsonArray names = new com.google.gson.JsonArray();
            for (String name : required) {
                names.add(name);
            }
            schema.add("required", names);
        }
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    /** A string property. */
    public static JsonObject stringProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        return property;
    }

    /** An integer property with an inclusive minimum. */
    public static JsonObject integerProperty(String description, int minimum) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "integer");
        property.addProperty("description", description);
        property.addProperty("minimum", minimum);
        return property;
    }

    /** A boolean property. */
    public static JsonObject booleanProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "boolean");
        property.addProperty("description", description);
        return property;
    }

    /** Reads a string argument, or the fallback when absent. */
    public static String argString(Map<String, Object> args, String key, String fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new McpToolError("'" + key + "' must be a non-empty string");
    }

    /** Reads a required string argument. */
    public static String argString(Map<String, Object> args, String key) {
        String value = argString(args, key, null);
        if (value == null) {
            throw new McpToolError("'" + key + "' is required");
        }
        return value;
    }

    /** Reads an integer argument, or the fallback when absent. */
    public static int argInt(Map<String, Object> args, String key, int fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new McpToolError("'" + key + "' must be an integer");
    }

    /** Reads a boolean argument, or the fallback when absent. */
    public static boolean argBool(Map<String, Object> args, String key, boolean fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new McpToolError("'" + key + "' must be a boolean");
    }

    /** Collects a bounded page of entries, newest within the limit, oldest first. */
    public static <T> List<T> page(List<T> entries, int limit) {
        return entries.size() <= limit
                ? entries : entries.subList(entries.size() - limit, entries.size());
    }
}
