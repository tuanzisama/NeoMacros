package ink.tuanzi.neomacros.fabric.web.http;

import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpExchange;
import ink.tuanzi.neomacros.fabric.web.json.Jsons;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One API request as the controllers see it: the raw request-line values
 * (which the signature covers), the request body already read by the filter
 * chain, the resolved path parameters, and the response surfaces. Handlers
 * that stream (SSE) take over the exchange with {@link #markHandled()}.
 */
public final class ApiContext {

    /** The largest request body accepted. */
    public static final int MAX_BODY_BYTES = 1024 * 1024;

    private final HttpExchange exchange;
    private final String method;
    private final String rawPath;
    private final String rawQuery;
    private final byte[] body;
    final Map<String, String> pathParams = new LinkedHashMap<>();
    private boolean handled;

    /** Creates the context; the body must already be read. */
    public ApiContext(HttpExchange exchange, String method, String rawPath, String rawQuery,
                      byte[] body) {
        this.exchange = exchange;
        this.method = method;
        this.rawPath = rawPath;
        this.rawQuery = rawQuery;
        this.body = body;
    }

    /** The HTTP method. */
    public String method() {
        return method;
    }

    /** The raw (still percent-encoded) request path, signature-relevant. */
    public String rawPath() {
        return rawPath;
    }

    /** The raw query string (without the question mark), possibly empty. */
    public String rawQuery() {
        return rawQuery;
    }

    /** The request body bytes. */
    public byte[] body() {
        return body;
    }

    /** The source address as host:port-ish string (backoff key). */
    public String source() {
        return String.valueOf(exchange.getRemoteAddress().getAddress());
    }

    /** One request header, or null. */
    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    /** The resolved path parameter with the given name. */
    public String pathParam(String name) {
        String value = pathParams.get(name);
        if (value == null) {
            throw new IllegalStateException("no path parameter: " + name);
        }
        return value;
    }

    /** Records a path parameter resolved by the router. */
    public void pathParam(String name, String value) {
        pathParams.put(name, value);
    }

    /** The first query parameter with the given name, or null. */
    public String queryParam(String name) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            if (decode(key, true).equals(name)) {
                String value = eq < 0 ? "" : pair.substring(eq + 1);
                return decode(value, true);
            }
        }
        return null;
    }

    /** Decodes the JSON body into the given record type. */
    public <T> T body(Class<T> type) {
        String text = new String(body, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw ApiError.invalidRequest("a JSON body is required");
        }
        try {
            return Jsons.recordOf(Jsons.parse(text), type);
        } catch (IllegalArgumentException e) {
            throw ApiError.invalidRequest(e.getMessage());
        }
    }

    /** The parsed JSON body as a generic tree. */
    public JsonElement bodyJson() {
        String text = new String(body, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw ApiError.invalidRequest("a JSON body is required");
        }
        try {
            return Jsons.parse(text);
        } catch (IllegalArgumentException e) {
            throw ApiError.invalidRequest(e.getMessage());
        }
    }

    /** Marks the response as already written (streaming handlers). */
    public void markHandled() {
        this.handled = true;
    }

    /** Whether the handler already wrote the response. */
    public boolean handled() {
        return handled;
    }

    /** The underlying exchange (streaming handlers only). */
    public HttpExchange exchange() {
        return exchange;
    }

    /** Reads the request body up to the cap; oversized or truncated → 400. */
    static byte[] readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        byte[] out = in.readNBytes(MAX_BODY_BYTES + 1);
        if (out.length > MAX_BODY_BYTES) {
            throw new BodyTooLarge();
        }
        return out;
    }

    /** Marker for the over-cap body case (no response written yet). */
    static final class BodyTooLarge extends RuntimeException {
    }

    /** Percent-decodes one path or query segment ('+' is a space only in queries). */
    static String decode(String text, boolean query) {
        var bytes = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '%' && i + 3 <= text.length()) {
                try {
                    bytes.write(Integer.parseInt(text.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException e) {
                    // fall through: keep the literal character
                }
            }
            if (c == '+' && query) {
                bytes.write(' ');
            } else {
                bytes.write(c);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** Writes a complete byte response with the given status and content type. */
    public static void respond(HttpExchange exchange, int status, String contentType,
                               byte[] payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
        if (payload.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        }
        exchange.close();
    }

    /** Parses the raw query string into an ordered map (first value wins). */
    public static Map<String, String> queryMap(String rawQuery) {
        Map<String, String> out = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return out;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq), true);
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1), true);
            out.putIfAbsent(key, value);
        }
        return out;
    }

    /** Splits a raw path into decoded segments. */
    public static List<String> segments(String rawPath) {
        var out = new java.util.ArrayList<String>();
        for (String segment : rawPath.split("/")) {
            if (!segment.isEmpty()) {
                out.add(decode(segment, false));
            }
        }
        return out;
    }
}
