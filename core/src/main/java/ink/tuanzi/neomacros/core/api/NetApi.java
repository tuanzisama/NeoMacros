package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.net.NetTrace;
import ink.tuanzi.neomacros.core.net.NetTraceEvent;
import ink.tuanzi.neomacros.core.util.JsonLite;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;

/**
 * The {@code neo.net} facade: HTTP client with fetch semantics. Requests run
 * on a dedicated IO executor and require the {@code net} capability; the
 * promise settles with a live {@link NetApi.Response} whose body readers
 * ({@code text()}, {@code json()}, {@code bytes()}) are async. Each settled
 * request is also recorded through {@link NetTrace} for the DevTools
 * network panel — previews always, phase timings, and full bodies up to
 * {@link #RETAIN_LIMIT} only while a sink listens. Recording is free when
 * no dispatcher listens.
 *
 * @zh {@code neo.net} 门面：fetch 语义的 HTTP 客户端。请求在专用 IO 线程池执行，需 net 能力授权；Promise 以活 {@code Response} 落定，其 body 读取器（{@code text()}、{@code json()}、{@code bytes()}）均为异步。每次请求落定会经 {@link NetTrace} 记录到 DevTools 网络面板——预览恒记，阶段计时与 ≤{@link #RETAIN_LIMIT} 的完整 body 仅在有监听时保留；无监听时记录近乎零开销。
 */
@ApiModule(name = "net", category = "系统")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class NetApi {

    /** Request body preview cap (UTF-16 chars). */
    private static final int REQUEST_PREVIEW_CHARS = 4096;

    /** Response body preview window (bytes, decoded as UTF-8). */
    private static final int RESPONSE_PREVIEW_BYTES = 4096;

    /** Full-body retention cap per side (request and response). */
    static final int RETAIN_LIMIT = 1024 * 1024;

    private final HttpClient client;
    private final ExecutorService ioExecutor;

    /** Creates the facade; the executor dispatches promise settlements. */
    public NetApi(ExecutorService ioExecutor) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.ioExecutor = ioExecutor;
    }

    /**
     * Performs an HTTP request with fetch semantics. The init object carries
     * {@code method} (default GET), {@code headers} (map) and {@code body}
     * (string).
     *
     * @zh 以 fetch 语义发起 HTTP 请求；init 携带 method/headers/body。
     * @zh-param url 绝对 http(s) URL
     * @zh-param init 请求初始化对象（可省略）
     * @zh-return Promise，以 Response 落定
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<Response>")
    @ApiPermission("net")
    public CompletableFuture<Response> fetch(String url, Object init) {
        Map<?, ?> options = init instanceof Map<?, ?> map ? map : Map.of();
        Object methodValue = options.get("method");
        String method = String.valueOf(methodValue != null ? methodValue : "GET")
                .toUpperCase(java.util.Locale.ROOT);
        Object body = options.get("body");
        long ts = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        String initiator = traceInitiator();
        String initiatorFile = traceInitiatorFile();
        Map<String, String> requestHeaders = headerMap(options.get("headers"));
        String bodyText = body instanceof String text && !text.isEmpty() ? text : null;
        String bodyPreview = bodyText == null ? null : preview(bodyText);
        boolean bodyTruncated = bodyText != null && bodyText.length() > REQUEST_PREVIEW_CHARS;
        String retainedBody = traceRetain() && bodyText != null
                && bodyText.length() <= RETAIN_LIMIT ? bodyText : null;
        // single-element bucket the timing handler stamps when headers land
        long[] headersNanos = new long[1];
        CompletableFuture<Response> future = new CompletableFuture<>();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));
            if (bodyText != null) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(
                        bodyText, StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            requestHeaders.forEach((key, value) -> builder.header(key, value));
            client.sendAsync(builder.build(), new TimingBodyHandler(headersNanos))
                    .whenComplete((response, error) -> {
                        // settle record leaves on the completion thread so the
                        // duration covers the full transfer, not the executor hop
                        NetTrace.dispatch(settledEvent(ts, startNanos, headersNanos[0],
                                method, url, initiator, initiatorFile, requestHeaders,
                                bodyPreview, retainedBody, bodyTruncated, response, error));
                        ioExecutor.execute(() -> {
                            if (error != null) {
                                future.completeExceptionally(error);
                            } else {
                                future.complete(new Response(response));
                            }
                        });
                    });
        } catch (RuntimeException error) {
            NetTrace.dispatch(new NetTraceEvent(ts, method, url, initiator, initiatorFile,
                    requestHeaders, bodyPreview, retainedBody, bodyTruncated,
                    null, null, null, null, false, false, null,
                    (System.nanoTime() - startNanos) / 1_000_000L, null, null,
                    error.toString()));
            future.completeExceptionally(error);
        }
        return future;
    }

    /**
     * Performs an HTTP GET; convenience form of {@link #fetch}.
     *
     * @zh 发起 HTTP GET（fetch 的便捷形式）。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<Response>")
    @ApiPermission("net")
    public CompletableFuture<Response> get(String url) {
        return fetch(url, null);
    }

    /**
     * Performs an HTTP POST with a string body; convenience form of
     * {@link #fetch}.
     *
     * @zh 发起带文本请求体的 HTTP POST（fetch 的便捷形式）。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<Response>")
    @ApiPermission("net")
    public CompletableFuture<Response> post(String url, String body) {
        Map<String, Object> init = new LinkedHashMap<>();
        init.put("method", "POST");
        init.put("body", body == null ? "" : body);
        return fetch(url, init);
    }

    /** The macro owning the calling context loop, or null outside macros. */
    private static String traceInitiator() {
        var module = LoadTracker.current();
        return module != null ? module.entry().name() : null;
    }

    /** The calling macro's entry script path, or null outside macros. */
    private static String traceInitiatorFile() {
        var module = LoadTracker.current();
        return module != null ? module.entry().entryFile().toString() : null;
    }

    /** Full-body retention is only worth its copy when a sink listens. */
    private static boolean traceRetain() {
        return NetTrace.hasDispatchers();
    }

    /** Stringified copy of a script-provided header map (null-safe). */
    private static Map<String, String> headerMap(Object headers) {
        Map<String, String> out = new LinkedHashMap<>();
        if (headers instanceof Map<?, ?> map) {
            map.forEach((key, value) -> out.put(
                    String.valueOf(key), String.valueOf(value)));
        }
        return out;
    }

    /** Assembles the settle record: response fields on success, error on failure. */
    private static NetTraceEvent settledEvent(long ts, long startNanos, long headersNanos,
            String method, String url, String initiator, String initiatorFile,
            Map<String, String> requestHeaders, String bodyPreview, String retainedBody,
            boolean bodyTruncated, HttpResponse<byte[]> response, Throwable error) {
        long endNanos = System.nanoTime();
        Long durationMs = (endNanos - startNanos) / 1_000_000L;
        Long waitMs = headersNanos != 0 ? (headersNanos - startNanos) / 1_000_000L : null;
        Long downloadMs = headersNanos != 0 ? (endNanos - headersNanos) / 1_000_000L : null;
        if (error != null) {
            return new NetTraceEvent(ts, method, url, initiator, initiatorFile,
                    requestHeaders, bodyPreview, retainedBody, bodyTruncated,
                    null, null, null, null, false, false, null,
                    durationMs, waitMs, downloadMs, error.toString());
        }
        byte[] bytes = response.body() != null ? response.body() : new byte[0];
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        response.headers().map().forEach((key, values) ->
                responseHeaders.put(key.toLowerCase(java.util.Locale.ROOT),
                        String.join(", ", values)));
        String contentType = responseHeaders.get("content-type");
        String preview = null;
        boolean truncated = bytes.length > RESPONSE_PREVIEW_BYTES;
        boolean binary = false;
        if (isTextual(contentType)) {
            preview = new String(Arrays.copyOf(
                    bytes, Math.min(bytes.length, RESPONSE_PREVIEW_BYTES)),
                    StandardCharsets.UTF_8);
        } else if (contentType == null || contentType.isBlank()) {
            // unlabeled bodies sniff for a NUL byte before committing to text
            byte[] slice = Arrays.copyOf(bytes, Math.min(bytes.length, RESPONSE_PREVIEW_BYTES));
            binary = containsNul(slice);
            preview = binary ? null : new String(slice, StandardCharsets.UTF_8);
            if (binary) {
                truncated = false;
            }
        } else {
            binary = true;
            truncated = false;
        }
        // retained body is a defensive copy: the live array backs the script's
        // bytes() view and must not alias the record
        byte[] retained = traceRetain() && bytes.length <= RETAIN_LIMIT
                ? Arrays.copyOf(bytes, bytes.length) : null;
        return new NetTraceEvent(ts, method, url, initiator, initiatorFile,
                requestHeaders, bodyPreview, retainedBody, bodyTruncated,
                response.statusCode(), responseHeaders, preview, retained, truncated, binary,
                (long) bytes.length, durationMs, waitMs, downloadMs, null);
    }

    /** Whether a content type carries readable text (JSON/XML/YAML included). */
    private static boolean isTextual(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String ct = contentType.toLowerCase(java.util.Locale.ROOT);
        return ct.startsWith("text/")
                || ct.contains("json") || ct.contains("xml")
                || ct.contains("javascript") || ct.contains("urlencoded")
                || ct.contains("html") || ct.contains("yaml");
    }

    /** Whether the slice contains a NUL byte (quick binary sniff). */
    private static boolean containsNul(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    /** Size-capped preview of a request body string. */
    private static String preview(String text) {
        return text.length() <= REQUEST_PREVIEW_CHARS
                ? text : text.substring(0, REQUEST_PREVIEW_CHARS);
    }

    /**
     * Wraps the byte-array handler to timestamp when the response headers
     * land: the subscriber starts right after headers are processed, before
     * body bytes flow. The bucket stays 0 when the exchange fails before
     * headers, which collapses the phase timings to null.
     */
    private static final class TimingBodyHandler implements HttpResponse.BodyHandler<byte[]> {

        private final long[] headersNanos;

        private TimingBodyHandler(long[] headersNanos) {
            this.headersNanos = headersNanos;
        }

        @Override
        public HttpResponse.BodySubscriber<byte[]> apply(HttpResponse.ResponseInfo info) {
            HttpResponse.BodySubscriber<byte[]> delegate =
                    HttpResponse.BodyHandlers.ofByteArray().apply(info);
            return new HttpResponse.BodySubscriber<>() {
                @Override
                public java.util.concurrent.CompletionStage<byte[]> getBody() {
                    return delegate.getBody();
                }

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    headersNanos[0] = System.nanoTime();
                    delegate.onSubscribe(subscription);
                }

                @Override
                public void onNext(List<ByteBuffer> item) {
                    delegate.onNext(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    delegate.onError(throwable);
                }

                @Override
                public void onComplete() {
                    delegate.onComplete();
                }
            };
        }
    }

    /**
     * Live HTTP response: status/header properties plus async body readers
     * ({@code text()}, {@code json()}, {@code bytes()}).
     */
    public static final class Response implements ScriptHandle {

        private final int status;
        private final Map<String, String> headers;
        private final byte[] body;

        Response(HttpResponse<byte[]> response) {
            this.status = response.statusCode();
            this.headers = new LinkedHashMap<>();
            response.headers().map().forEach((key, values) ->
                    headers.put(key.toLowerCase(java.util.Locale.ROOT),
                            String.join(", ", values)));
            this.body = response.body() != null ? response.body() : new byte[0];
        }

        /** The HTTP status code. */
        public int getStatus() {
            return status;
        }

        /** Whether the status is in the 2xx range. */
        public boolean getOk() {
            return status >= 200 && status < 300;
        }

        /** Response headers, keyed by lower-cased name. */
        public Map<String, String> getHeaders() {
            return headers;
        }

        /** Reads the body as UTF-8 text. */
        @ApiSince("0.1.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<string>")
        public CompletableFuture<String> text() {
            return CompletableFuture.completedFuture(
                    new String(body, StandardCharsets.UTF_8));
        }

        /** Parses the body as JSON; rejects when the body is not valid JSON. */
        @ApiSince("0.1.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<any>")
        public CompletableFuture<Object> json() {
            try {
                return CompletableFuture.completedFuture(
                        JsonLite.parse(new String(body, StandardCharsets.UTF_8)));
            } catch (RuntimeException error) {
                CompletableFuture<Object> failed = new CompletableFuture<>();
                failed.completeExceptionally(
                        new IllegalStateException("response body is not valid JSON", error));
                return failed;
            }
        }

        /** Reads the body as raw bytes (rendered as a Uint8Array). */
        @ApiSince("0.1.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<Uint8Array>")
        public CompletableFuture<byte[]> bytes() {
            return CompletableFuture.completedFuture(body);
        }
    }
}
