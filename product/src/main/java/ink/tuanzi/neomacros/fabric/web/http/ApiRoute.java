package ink.tuanzi.neomacros.fabric.web.http;

import ink.tuanzi.neomacros.fabric.web.api.NmApi;
import ink.tuanzi.neomacros.fabric.web.api.NmBody;
import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import ink.tuanzi.neomacros.fabric.web.api.NmError;
import ink.tuanzi.neomacros.fabric.web.api.NmGet;
import ink.tuanzi.neomacros.fabric.web.api.NmParam;
import ink.tuanzi.neomacros.fabric.web.api.NmPost;
import ink.tuanzi.neomacros.fabric.web.api.NmPut;
import ink.tuanzi.neomacros.fabric.web.api.NmDelete;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One scanned route: the HTTP verb, the path template, the generator
 * metadata (tag/summary/auth/errors/parameter docs), and the reflective
 * invoker that binds ApiContext, {@code @NmBody}, and {@code @NmParam}
 * parameters onto the controller method.
 */
public final class ApiRoute {

    /** A template parameter: its name and whether it swallows several segments. */
    public record TemplateParam(String name, boolean multi) {
    }

    /** A handler signature parameter documented for the generator. */
    public record ParamInfo(String name, boolean inPath, Class<?> type, String desc) {
    }

    /** The bound path parameters of a successful match. */
    public record Bindings(Map<String, String> pathParams) {
    }

    private final String method;
    private final String template;
    private final String[] segments;
    private final List<TemplateParam> templateParams;
    private final NmApi api;
    private final List<NmError> errors;
    private final Method handler;
    private final Object controller;

    private ApiRoute(String method, String template, NmApi api, List<NmError> errors,
                     Method handler, Object controller) {
        this.method = method;
        this.template = template;
        this.segments = template.split("/");
        this.templateParams = parseParams(template);
        this.api = api;
        this.errors = errors;
        this.handler = handler;
        this.controller = controller;
        handler.setAccessible(true);
    }

    /** Scans one controller object for annotated route methods. */
    public static List<ApiRoute> scan(Object controller) {
        List<ApiRoute> routes = new ArrayList<>();
        for (Method method : controller.getClass().getDeclaredMethods()) {
            addRoute(routes, method, controller, method.getAnnotation(NmGet.class), "GET");
            addRoute(routes, method, controller, method.getAnnotation(NmPost.class), "POST");
            addRoute(routes, method, controller, method.getAnnotation(NmPut.class), "PUT");
            addRoute(routes, method, controller, method.getAnnotation(NmDelete.class), "DELETE");
        }
        return routes;
    }

    private static void addRoute(List<ApiRoute> routes, Method method, Object controller,
                                 Annotation annotation, String verb) {
        if (annotation == null) {
            return;
        }
        String template;
        try {
            template = (String) annotation.annotationType().getMethod("value").invoke(annotation);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("route annotation without a path", e);
        }
        NmApi api = method.getAnnotation(NmApi.class);
        if (api == null) {
            throw new IllegalStateException("route method without @NmApi: " + method);
        }
        routes.add(new ApiRoute(verb, template, api,
                List.of(method.getAnnotationsByType(NmError.class)), method, controller));
    }

    /** Extracts {@code {name}} and {@code {name+}} parameters from a template. */
    private static List<TemplateParam> parseParams(String template) {
        List<TemplateParam> params = new ArrayList<>();
        for (String segment : template.split("/")) {
            if (segment.startsWith("{") && segment.endsWith("}")) {
                String inner = segment.substring(1, segment.length() - 1);
                boolean multi = inner.endsWith("+");
                params.add(new TemplateParam(
                        multi ? inner.substring(0, inner.length() - 1) : inner, multi));
            }
        }
        return params;
    }

    public String method() {
        return method;
    }

    /** The path template as declared (parameter placeholders intact). */
    public String template() {
        return template;
    }

    public NmApi api() {
        return api;
    }

    public List<NmError> errors() {
        return errors;
    }

    /** The template parameters in declaration order. */
    public List<TemplateParam> templateParams() {
        return templateParams;
    }

    /** The response type (the handler's return type). */
    public Class<?> responseType() {
        return handler.getReturnType();
    }

    /** The annotated request-body type, or null. */
    public Class<?> bodyType() {
        for (Parameter parameter : handler.getParameters()) {
            if (parameter.isAnnotationPresent(NmBody.class)) {
                return parameter.getType();
            }
        }
        return null;
    }

    /** Collects the documented {@code @NmParam} parameters from the signature. */
    public List<ParamInfo> documentedParams() {
        List<ParamInfo> out = new ArrayList<>();
        for (Parameter parameter : handler.getParameters()) {
            NmParam nmParam = parameter.getAnnotation(NmParam.class);
            if (nmParam == null) {
                continue;
            }
            NmDesc desc = parameter.getAnnotation(NmDesc.class);
            boolean inPath = templateParams.stream()
                    .anyMatch(p -> p.name().equals(nmParam.value()) && p.multi() == nmParam.multi());
            out.add(new ParamInfo(nmParam.value(), inPath, parameter.getType(),
                    desc == null ? null : desc.value()));
        }
        return out;
    }

    /**
     * Whether this route's verb and path shape match a request. A multi
     * parameter greedily consumes everything up to the trailing literal
     * segments; on success the bound values are returned.
     */
    public Bindings match(String verb, List<String> requestSegments) {
        if (!method.equals(verb)) {
            return null;
        }
        int[] cursor = {0};
        Map<String, String> bound = new LinkedHashMap<>();
        for (int t = 0; t < segments.length; t++) {
            String segment = segments[t];
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.startsWith("{") && segment.endsWith("}")) {
                String inner = segment.substring(1, segment.length() - 1);
                boolean multi = inner.endsWith("+");
                String name = multi ? inner.substring(0, inner.length() - 1) : inner;
                if (multi) {
                    int remaining = 0;
                    for (int rest = t + 1; rest < segments.length; rest++) {
                        if (!segments[rest].isEmpty()) {
                            remaining++;
                        }
                    }
                    int available = requestSegments.size() - cursor[0] - remaining;
                    if (available < 1) {
                        return null;
                    }
                    var joined = new StringBuilder();
                    for (int taken = 0; taken < available; taken++) {
                        if (taken > 0) {
                            joined.append('/');
                        }
                        joined.append(requestSegments.get(cursor[0]++));
                    }
                    bound.put(name, joined.toString());
                } else {
                    if (cursor[0] >= requestSegments.size()) {
                        return null;
                    }
                    bound.put(name, requestSegments.get(cursor[0]++));
                }
            } else {
                if (cursor[0] >= requestSegments.size()
                        || !segment.equals(requestSegments.get(cursor[0]++))) {
                    return null;
                }
            }
        }
        if (cursor[0] != requestSegments.size()) {
            return null;
        }
        return new Bindings(bound);
    }

    /**
     * Invokes the handler with the context: binds the ApiContext, body, and
     * parameter values by signature. Returns the handler's response value.
     */
    public Object invoke(ApiContext context) throws Exception {
        Parameter[] parameters = handler.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter.getType() == ApiContext.class) {
                args[i] = context;
            } else if (parameter.isAnnotationPresent(NmBody.class)) {
                args[i] = context.body(parameter.getType());
            } else {
                NmParam nmParam = parameter.getAnnotation(NmParam.class);
                if (nmParam == null) {
                    throw new IllegalStateException(
                            "unbindable parameter in " + handler.getName());
                }
                String raw = context.pathParams.get(nmParam.value());
                if (raw == null) {
                    raw = context.queryParam(nmParam.value());
                }
                args[i] = coerce(raw, parameter.getType(), nmParam.value());
            }
        }
        try {
            return handler.invoke(controller, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private static Object coerce(String raw, Class<?> type, String name) {
        if (raw == null) {
            if (type == long.class || type == int.class || type == boolean.class) {
                throw ApiError.invalidRequest("missing parameter: " + name);
            }
            return null;
        }
        if (type == String.class) {
            return raw;
        }
        try {
            if (type == long.class || type == Long.class) {
                return Long.parseLong(raw);
            }
            if (type == int.class || type == Integer.class) {
                return Integer.parseInt(raw);
            }
            if (type == boolean.class || type == Boolean.class) {
                return Boolean.parseBoolean(raw);
            }
        } catch (NumberFormatException e) {
            throw ApiError.invalidRequest("parameter '" + name + "' must be a number");
        }
        throw new IllegalStateException("unsupported parameter type: " + type);
    }
}
