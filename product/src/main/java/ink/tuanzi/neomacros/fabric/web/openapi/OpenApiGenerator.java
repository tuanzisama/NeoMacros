package ink.tuanzi.neomacros.fabric.web.openapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ink.tuanzi.neomacros.fabric.web.api.NmAuth;
import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import ink.tuanzi.neomacros.fabric.web.api.NmError;
import ink.tuanzi.neomacros.fabric.web.http.ApiRoute;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the OpenAPI 3.0 document from the scanned routes: the annotations
 * and typed records are the single source of truth. The output is a Gson
 * tree; {@link ink.tuanzi.neomacros.fabric.web.json.Jsons#stringify(Object,
 * boolean)} with sorted keys renders it byte-stably for the golden test and
 * the {@code /api-docs} endpoint.
 */
public final class OpenApiGenerator {

    private final List<ApiRoute> routes;
    private final String title;
    private final String version;
    private final String gameVersion;
    private final int port;

    /** Creates the generator; the port lands in {@code servers} (0 = placeholder). */
    public OpenApiGenerator(List<ApiRoute> routes, String title, String version,
                            String gameVersion, int port) {
        this.routes = routes;
        this.title = title;
        this.version = version;
        this.gameVersion = gameVersion;
        this.port = port;
    }

    /** Builds the complete document. */
    public JsonObject generate() {
        JsonObject root = new JsonObject();
        root.addProperty("openapi", "3.0.3");

        JsonObject info = new JsonObject();
        info.addProperty("title", title);
        info.addProperty("version", version);
        info.addProperty("x-game-version", gameVersion);
        root.add("info", info);

        JsonArray servers = new JsonArray();
        JsonObject server = new JsonObject();
        server.addProperty("url", "http://127.0.0.1:" + (port == 0 ? "{port}" : port));
        if (port == 0) {
            JsonObject variables = new JsonObject();
            JsonObject portVar = new JsonObject();
            portVar.addProperty("default", "{port}");
            variables.add("port", portVar);
            server.add("variables", variables);
        }
        servers.add(server);
        root.add("servers", servers);

        root.add("tags", tags());
        root.add("paths", paths());
        root.add("components", components());
        return root;
    }

    private JsonArray tags() {
        // Sorted: reflection's method order is unstable across compilations
        // and the document must stay byte-stable.
        Set<String> names = new LinkedHashSet<>();
        for (ApiRoute route : routes) {
            names.add(route.api().tag());
        }
        JsonArray tags = new JsonArray();
        names.stream().sorted().forEach(name -> {
            JsonObject tag = new JsonObject();
            tag.addProperty("name", name);
            tags.add(tag);
        });
        return tags;
    }

    private JsonObject paths() {
        JsonObject paths = new JsonObject();
        for (ApiRoute route : routes) {
            String path = openApiPath(route.template());
            JsonObject pathItem = paths.has(path)
                    ? paths.getAsJsonObject(path) : new JsonObject();
            paths.add(path, pathItem);
            pathItem.add(route.method().toLowerCase(), operation(route));
        }
        return paths;
    }

    /** Converts {@code {name+}} templates to OpenAPI's {@code {name}}. */
    private static String openApiPath(String template) {
        return template.replace("+}", "}");
    }

    private JsonObject operation(ApiRoute route) {
        JsonObject operation = new JsonObject();
        operation.addProperty("operationId", operationId(route));
        JsonArray tags = new JsonArray();
        tags.add(route.api().tag());
        operation.add("tags", tags);
        operation.addProperty("summary", route.api().summary());

        JsonArray parameters = new JsonArray();
        for (ApiRoute.ParamInfo param : route.documentedParams()) {
            JsonObject parameter = new JsonObject();
            parameter.addProperty("name", param.name());
            parameter.addProperty("in", param.inPath() ? "path" : "query");
            parameter.addProperty("required", param.inPath());
            JsonObject schema = new JsonObject();
            schema.addProperty("type", jsonType(param.type()));
            parameter.add("schema", schema);
            if (param.desc() != null) {
                parameter.addProperty("description", param.desc());
            }
            parameters.add(parameter);
        }
        if (parameters.size() > 0) {
            operation.add("parameters", parameters);
        }

        Class<?> bodyType = route.bodyType();
        if (bodyType != null) {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("required", true);
            JsonObject content = new JsonObject();
            content.add("application/json", mediaType(bodyType));
            requestBody.add("content", content);
            operation.add("requestBody", requestBody);
        }

        operation.add("responses", responses(route));
        JsonArray security = new JsonArray();
        if (route.api().auth() == NmAuth.NM1) {
            JsonObject requirement = new JsonObject();
            requirement.add("NM1", new JsonArray());
            security.add(requirement);
        } else if (route.api().auth() == NmAuth.TICKET) {
            JsonObject requirement = new JsonObject();
            requirement.add("SseTicket", new JsonArray());
            security.add(requirement);
        }
        operation.add("security", security);
        return operation;
    }

    private String operationId(ApiRoute route) {
        return route.template().replaceFirst("^/api/v1/", "")
                .replace("/", "_").replace("{", "").replace("+", "").replace("}", "")
                + "_" + route.method().toLowerCase();
    }

    private JsonObject responses(ApiRoute route) {
        JsonObject responses = new JsonObject();
        Class<?> responseType = route.responseType();
        if (responseType == void.class || responseType == Void.class) {
            JsonObject response = new JsonObject();
            response.addProperty("description", "No content");
            responses.add("204", response);
        } else {
            JsonObject response = new JsonObject();
            response.addProperty("description", "Success");
            JsonObject content = new JsonObject();
            content.add("application/json", mediaType(responseType));
            response.add("content", content);
            responses.add("200", response);
        }
        for (NmError error : route.errors()) {
            JsonObject response = new JsonObject();
            response.addProperty("description", error.desc());
            JsonObject content = new JsonObject();
            content.add("application/json", ref("#/components/schemas/ApiErrorBody"));
            response.add("content", content);
            responses.add(String.valueOf(error.status()), response);
        }
        return responses;
    }

    private JsonObject mediaType(Class<?> type) {
        JsonObject media = new JsonObject();
        media.add("schema", new SchemaMapper().schemaOf(type));
        return media;
    }

    private static JsonObject ref(String reference) {
        JsonObject media = new JsonObject();
        JsonObject schema = new JsonObject();
        schema.addProperty("$ref", reference);
        media.add("schema", schema);
        return media;
    }

    private static String jsonType(Class<?> type) {
        if (type == long.class || type == int.class || type == Long.class
                || type == Integer.class) {
            return "integer";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        return "string";
    }

    private JsonObject components() {
        SchemaMapper mapper = new SchemaMapper();
        for (ApiRoute route : routes) {
            if (route.bodyType() != null) {
                mapper.schemaOf(route.bodyType());
            }
            if (route.responseType() != void.class && route.responseType() != Void.class) {
                mapper.schemaOf(route.responseType());
            }
        }
        JsonObject components = new JsonObject();

        JsonObject securitySchemes = new JsonObject();
        JsonObject nm1 = new JsonObject();
        nm1.addProperty("type", "apiKey");
        nm1.addProperty("in", "header");
        nm1.addProperty("name", "Authorization");
        nm1.addProperty("description",
                "NeoMacros1 id=…, ts=…, nonce=…, bh=…, sig=… — the NM1 per-request"
                        + " Ed25519 signature over the canonical signing string.");
        securitySchemes.add("NM1", nm1);
        JsonObject ticket = new JsonObject();
        ticket.addProperty("type", "apiKey");
        ticket.addProperty("in", "query");
        ticket.addProperty("name", "ticket");
        ticket.addProperty("description",
                "A one-time ticket valid for 30 seconds, obtained from the signed"
                        + " ticket endpoint (EventSource cannot send headers).");
        securitySchemes.add("SseTicket", ticket);
        components.add("securitySchemes", securitySchemes);

        JsonObject schemas = new JsonObject();
        mapper.collected().forEach(schemas::add);
        schemas.add("ApiErrorBody", errorSchema());
        components.add("schemas", schemas);
        return components;
    }

    private static JsonObject errorSchema() {
        JsonObject detail = new JsonObject();
        detail.addProperty("type", "object");
        var required = new JsonArray();
        required.add("code");
        required.add("message");
        detail.add("required", required);
        JsonObject properties = new JsonObject();
        JsonObject code = new JsonObject();
        code.addProperty("type", "string");
        code.addProperty("description", "unauthorized / forbidden / not_found / conflict /"
                + " too_many_requests / game_thread_timeout / invalid_request / timeout /"
                + " internal");
        properties.add("code", code);
        JsonObject message = new JsonObject();
        message.addProperty("type", "string");
        properties.add("message", message);
        detail.add("properties", properties);

        JsonObject body = new JsonObject();
        body.addProperty("type", "object");
        var bodyRequired = new JsonArray();
        bodyRequired.add("error");
        body.add("required", bodyRequired);
        JsonObject bodyProperties = new JsonObject();
        bodyProperties.add("error", refLocal(detail));
        body.add("properties", bodyProperties);
        return body;
    }

    private static JsonObject refLocal(JsonObject schema) {
        JsonArray allOf = new JsonArray();
        allOf.add(schema);
        JsonObject wrapper = new JsonObject();
        wrapper.add("allOf", allOf);
        return wrapper;
    }
}
