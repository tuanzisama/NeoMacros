package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.core.util.JsonLite;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * The {@code neo.net} facade: HTTP client with fetch semantics. Requests run
 * on a dedicated IO executor and require the {@code net} capability; the
 * promise settles with a live {@link NetApi.Response} whose body readers
 * ({@code text()}, {@code json()}, {@code bytes()}) are async.
 *
 * @zh {@code neo.net} 门面：fetch 语义的 HTTP 客户端。请求在专用 IO 线程池执行，需 net 能力授权；Promise 以活 {@code Response} 落定，其 body 读取器（{@code text()}、{@code json()}、{@code bytes()}）均为异步。
 */
@ApiModule(name = "net", category = "系统")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class NetApi {

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
        CompletableFuture<Response> future = new CompletableFuture<>();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));
            if (body instanceof String text && !text.isEmpty()) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(
                        text, StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            if (options.get("headers") instanceof Map<?, ?> headers) {
                headers.forEach((key, value) -> builder.header(
                        String.valueOf(key), String.valueOf(value)));
            }
            client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                    .whenComplete((response, error) -> ioExecutor.execute(() -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            future.complete(new Response(response));
                        }
                    }));
        } catch (RuntimeException error) {
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

    /**
     * Opens a WebSocket connection and resolves with the live socket once
     * the handshake completes; handshake failures reject. Requires the
     * {@code net} capability (module-level gate).
     *
     * @zh 打开 WebSocket 连接，握手完成后以活连接落定；握手失败 reject。需 net 能力授权。
     * @zh-param url ws(s):// URL
     * @zh-return Promise，以 {@code Ws} 落定
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<Ws>")
    public CompletableFuture<Ws> connect(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException e) {
            CompletableFuture<Ws> failed = new CompletableFuture<>();
            failed.completeExceptionally(
                    new IllegalArgumentException("invalid websocket url: " + url, e));
            return failed;
        }
        CompletableFuture<Ws> future = new CompletableFuture<>();
        Ws handle = new Ws(uri);
        client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, handle.listener()).whenComplete((socket, error) -> {
                    if (error != null) {
                        future.completeExceptionally(error);
                        handle.dispatch("error", error.toString());
                    } else {
                        handle.attach(socket);
                        future.complete(handle);
                        handle.dispatch("open", null);
                    }
                });
        return future;
    }

    /**
     * Live WebSocket connection. Events surface through {@code on} with the
     * names {@code open}, {@code message} (string payload), {@code close},
     * and {@code error} (message string).
     *
     * @zh 活的 WebSocket 连接。事件经 {@code on} 订阅：{@code open}、{@code message}（字符串载荷）、{@code close}、{@code error}（错误消息）。
     */
    @ApiClass(name = "Ws", kind = ApiClassKind.HANDLE)
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class Ws implements ScriptHandle {

        private final URI uri;
        private final Map<String, java.util.List<java.util.function.Consumer<Object>>> listeners =
                new LinkedHashMap<>();
        private volatile java.net.http.WebSocket socket;
        private final StringBuilder fragment = new StringBuilder();

        Ws(URI uri) {
            this.uri = uri;
        }

        /** The connected URL.
         * @zh 连接的 URL。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getUrl() {
            return uri.toString();
        }

        /**
         * Subscribes one event handler ({@code open}/{@code message}/
         * {@code close}/{@code error}).
         *
         * @zh 订阅事件（{@code open}/{@code message}/{@code close}/{@code error}）。
         * @zh-param name 事件名
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        public void on(String name, java.util.function.Consumer<Object> handler) {
            if (!java.util.Set.of("open", "message", "close", "error").contains(name)) {
                throw new IllegalArgumentException("unknown websocket event: " + name);
            }
            synchronized (listeners) {
                listeners.computeIfAbsent(name,
                        key -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(handler);
            }
        }

        /**
         * Sends one text frame.
         *
         * @zh 发送一帧文本。
         * @zh-param data 文本内容
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        public void send(String data) {
            java.net.http.WebSocket current = socket;
            if (current == null) {
                throw new IllegalStateException("websocket is not open");
            }
            current.sendText(data, true);
        }

        /**
         * Closes the connection gracefully.
         *
         * @zh 优雅地关闭连接。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        public void close() {
            java.net.http.WebSocket current = socket;
            if (current != null) {
                current.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "");
            }
        }

        @Override
        public String toString() {
            return "Ws(" + uri + ")";
        }

        /** Attaches the underlying socket once the handshake completes. */
        void attach(java.net.http.WebSocket socket) {
            this.socket = socket;
            socket.request(1);
        }

        /** Dispatches one event to the registered handlers. */
        void dispatch(String name, Object payload) {
            synchronized (listeners) {
                java.util.List<java.util.function.Consumer<Object>> handlers = listeners.get(name);
                if (handlers == null) {
                    return;
                }
                for (java.util.function.Consumer<Object> handler : handlers) {
                    try {
                        handler.accept(payload);
                    } catch (RuntimeException ignored) {
                        // one failing handler must not break the socket pump
                    }
                }
            }
        }

        /** Builds the java HttpClient listener driving the event dispatch. */
        java.net.http.WebSocket.Listener listener() {
            return new java.net.http.WebSocket.Listener() {
                @Override
                public void onOpen(java.net.http.WebSocket webSocket) {
                    webSocket.request(1);
                }

                @Override
                public java.util.concurrent.CompletionStage<?> onText(
                        java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
                    fragment.append(data);
                    if (last) {
                        dispatch("message", fragment.toString());
                        fragment.setLength(0);
                    }
                    webSocket.request(1);
                    return null;
                }

                @Override
                public java.util.concurrent.CompletionStage<?> onClose(
                        java.net.http.WebSocket webSocket, int statusCode, String reason) {
                    dispatch("close", reason);
                    return null;
                }

                @Override
                public void onError(java.net.http.WebSocket webSocket, Throwable error) {
                    dispatch("error", String.valueOf(error));
                }
            };
        }
    }
}
