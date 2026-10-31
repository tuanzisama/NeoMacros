package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * The {@code neo.net} facade: HTTP client (fetch-style). Requests run on a
 * dedicated IO executor and require the {@code net} capability.
 */
@ApiModule(name = "net", category = "系统")
public final class NetApi {

    private final HttpClient client;
    private final ExecutorService ioExecutor;

    /** Creates the facade; the executor dispatches promise settlements. */
    public NetApi(ExecutorService ioExecutor) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.ioExecutor = ioExecutor;
    }

    /**
     * Performs an HTTP GET; the promise settles with a response map
     * ({@code status}, {@code body}, {@code headers}).
     *
     * @param url absolute http(s) URL
     * @zh 发起 HTTP GET，Promise 以 {status, body, headers} 落定。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<object>")
    @ApiPermission("net")
    public CompletableFuture<Map<String, Object>> get(String url) {
        return request("GET", url, null, null);
    }

    /**
     * Performs an HTTP request with method, body and headers.
     *
     * @param method HTTP method (GET/POST/PUT/DELETE/...)
     * @param url absolute http(s) URL
     * @param body request body text (nullable)
     * @param headers request headers (nullable map)
     * @zh 发起带方法/请求体/请求头的 HTTP 请求。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<object>")
    @ApiPermission("net")
    public CompletableFuture<Map<String, Object>> request(String method, String url,
                                                          String body, Map<String, String> headers) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));
            if (body != null) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            if (headers != null) {
                headers.forEach(builder::header);
            }
            client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .whenComplete((response, error) -> ioExecutor.execute(() -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                            return;
                        }
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("status", response.statusCode());
                        out.put("body", response.body());
                        Map<String, List<String>> headerMap = new LinkedHashMap<>();
                        response.headers().map().forEach(headerMap::put);
                        out.put("headers", headerMap);
                        future.complete(out);
                    }));
        } catch (RuntimeException error) {
            future.completeExceptionally(error);
        }
        return future;
    }
}
