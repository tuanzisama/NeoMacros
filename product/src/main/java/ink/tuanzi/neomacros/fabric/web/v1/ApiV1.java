package ink.tuanzi.neomacros.fabric.web.v1;

import com.sun.net.httpserver.HttpExchange;
import ink.tuanzi.neomacros.core.console.ScriptConsoleEvent;
import ink.tuanzi.neomacros.fabric.web.FileContent;
import ink.tuanzi.neomacros.fabric.web.FileWriteResult;
import ink.tuanzi.neomacros.fabric.web.GameRunner;
import ink.tuanzi.neomacros.fabric.web.PanelBackend;
import ink.tuanzi.neomacros.fabric.web.api.NmApi;
import ink.tuanzi.neomacros.fabric.web.api.NmAuth;
import ink.tuanzi.neomacros.fabric.web.api.NmBody;
import ink.tuanzi.neomacros.fabric.web.api.NmDelete;
import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import ink.tuanzi.neomacros.fabric.web.api.NmError;
import ink.tuanzi.neomacros.fabric.web.api.NmGet;
import ink.tuanzi.neomacros.fabric.web.api.NmParam;
import ink.tuanzi.neomacros.fabric.web.api.NmPost;
import ink.tuanzi.neomacros.fabric.web.api.NmPut;
import ink.tuanzi.neomacros.fabric.web.auth.DeviceRecord;
import ink.tuanzi.neomacros.fabric.web.auth.DeviceRegistry;
import ink.tuanzi.neomacros.fabric.web.console.ConsoleRing;
import ink.tuanzi.neomacros.fabric.web.http.ApiContext;
import ink.tuanzi.neomacros.fabric.web.http.ApiError;
import ink.tuanzi.neomacros.fabric.web.json.Jsons;
import ink.tuanzi.neomacros.fabric.web.log.LogRing;
import ink.tuanzi.neomacros.fabric.web.net.NetRing;
import ink.tuanzi.neomacros.fabric.web.pair.PairingRequests;
import ink.tuanzi.neomacros.fabric.web.pair.PairingService;
import ink.tuanzi.neomacros.fabric.web.sse.SseHub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The web panel API surface: version probe, pairing (deep-link token and
 * tokenless with the in-game decision), state snapshot, macro lifecycle
 * actions with create/delete, the file editor API, device management, log
 * replay, and the SSE stream with its ticket exchange. Every game-state
 * read and macro action is dispatched onto the game main thread with a
 * five-second budget.
 */
public final class ApiV1 {

    private final PanelBackend backend;
    private final GameRunner game;
    private final PairingService pairing;
    private final PairingRequests pairingRequests;
    private final Predicate<String> originAllowed;
    private final Consumer<PairingRequests.Request> pairRequestListener;
    private final DeviceRegistry devices;
    private final SseHub sse;
    private final LogRing logs;
    private final ConsoleRing console;
    private final NetRing net;

    /**
     * Creates the controller. The origin predicate is the CORS allowlist
     * gate for tokenless pairing; the listener (nullable in tests) is the
     * game-side hook that renders the in-game decision card.
     */
    public ApiV1(PanelBackend backend, GameRunner game, PairingService pairing,
                 PairingRequests pairingRequests, Predicate<String> originAllowed,
                 Consumer<PairingRequests.Request> pairRequestListener,
                 DeviceRegistry devices, SseHub sse, LogRing logs, ConsoleRing console,
                 NetRing net) {
        this.backend = backend;
        this.game = game;
        this.pairing = pairing;
        this.pairingRequests = pairingRequests;
        this.originAllowed = originAllowed;
        this.pairRequestListener = pairRequestListener;
        this.devices = devices;
        this.sse = sse;
        this.logs = logs;
        this.console = console;
        this.net = net;
    }

    @NmGet("/api/v1/meta")
    @NmApi(tag = "meta", summary = "Version negotiation probe (unsigned)", auth = NmAuth.NONE)
    @NmError(status = 403, code = "forbidden",
            desc = "The Host header is outside the allowlist (rebinding defense)")
    public Meta meta() {
        return backend.meta();
    }

    @NmPost("/api/v1/pair")
    @NmApi(tag = "pairing", summary = "Pair a device with the one-time deep-link token"
            + " (single use, long-lived and configurable)", auth = NmAuth.NONE)
    @NmError(status = 400, code = "invalid_request",
            desc = "Malformed request or an invalid, expired, or already-used token")
    public PairResponse pair(@NmBody PairRequest request) {
        if (request.token() == null || request.token().isBlank()
                || request.label() == null || request.label().isBlank()
                || request.algo() == null || request.algo().isBlank()
                || request.pubJwk() == null || request.pubJwk().isEmpty()) {
            throw ApiError.invalidRequest(
                    "the pairing request needs token, label, algo, and pubJwk");
        }
        if (!pairing.validate(request.token())) {
            throw ApiError.invalidRequest(
                    "invalid, expired, or already-used pairing token");
        }
        try {
            ink.tuanzi.neomacros.fabric.web.auth.Nm1Keys.publicKey(
                    request.algo(), request.pubJwk());
        } catch (IllegalArgumentException e) {
            throw ApiError.invalidRequest("unusable public key: " + e.getMessage());
        }
        DeviceRecord device = devices.register(
                request.label(), request.algo(), request.pubJwk());
        pairing.consume(request.token());
        return new PairResponse(device.id());
    }

    @NmPost("/api/v1/pair-request")
    @NmApi(tag = "pairing", summary = "Request pairing without a token (loopback browsers"
            + " only, Origin-gated); answers immediately under loopback trust, otherwise"
            + " waits for the in-game decision", auth = NmAuth.NONE)
    @NmError(status = 400, code = "invalid_request", desc = "Malformed request or unusable key")
    @NmError(status = 403, code = "forbidden",
            desc = "The Origin is not allowed or the request is not from loopback")
    public PairApplyResponse requestPair(@NmBody PairApplyRequest request, ApiContext context) {
        if (request.label() == null || request.label().isBlank()
                || request.algo() == null || request.algo().isBlank()
                || request.pubJwk() == null || request.pubJwk().isEmpty()) {
            throw ApiError.invalidRequest(
                    "the pairing request needs label, algo, and pubJwk");
        }
        try {
            ink.tuanzi.neomacros.fabric.web.auth.Nm1Keys.publicKey(
                    request.algo(), request.pubJwk());
        } catch (IllegalArgumentException e) {
            throw ApiError.invalidRequest("unusable public key: " + e.getMessage());
        }
        // Gate 1: the Origin header must be panel-allowlisted (browsers set
        // it truthfully, so this keeps drive-by web pages out).
        if (!originAllowed.test(context.header("Origin"))) {
            throw ApiError.forbidden(
                    "request Origin is not allowed for tokenless pairing");
        }
        // Gate 2: loopback sources only — LAN devices pair with a deep-link
        // token. A local process is already trusted with the game, so the
        // source check is the hard boundary and Origin is the anti-drive-by.
        java.net.InetAddress source = context.sourceAddress();
        if (source == null || !source.isLoopbackAddress()) {
            throw ApiError.forbidden("tokenless pairing is served to loopback"
                    + " browsers only; use a deep-link token from the game");
        }
        // Trust shortcut: a confirmed "trust this machine" decision answers
        // later loopback browsers without bothering the player again.
        if (pairing.trustLoopback()) {
            DeviceRecord device = devices.register(
                    request.label(), request.algo(), request.pubJwk());
            return PairApplyResponse.paired(device.id());
        }
        PairingRequests.Request submitted = pairingRequests.submit(
                request.label(), request.algo(), request.pubJwk(),
                context.header("Origin"), context.source());
        if (pairRequestListener != null) {
            pairRequestListener.accept(submitted);
        }
        return PairApplyResponse.pending(submitted.id(),
                (int) (PairingRequests.REQUEST_TTL_MILLIS / 1000));
    }

    @NmGet("/api/v1/pair-request/{id}")
    @NmApi(tag = "pairing", summary = "Poll one tokenless pairing request for the"
            + " in-game decision", auth = NmAuth.NONE)
    public PairApplyStatus pairRequestStatus(@NmParam("id") String id) {
        PairingRequests.Outcome outcome = pairingRequests.outcomeOf(id);
        if (outcome == null || outcome == PairingRequests.Outcome.EXPIRED) {
            return PairApplyStatus.expired();
        }
        return switch (outcome) {
            case PENDING -> new PairApplyStatus("pending", null);
            case APPROVED -> new PairApplyStatus("approved",
                    pairingRequests.clientIdOf(id));
            case DENIED -> new PairApplyStatus("denied", null);
            case EXPIRED -> PairApplyStatus.expired();
        };
    }

    @NmGet("/api/v1/state")
    @NmApi(tag = "macros", summary = "Full state snapshot (panel first screen)")
    @NmError(status = 401, code = "unauthorized",
            desc = "Request unsigned or signature verification failed")
    @NmError(status = 504, code = "game_thread_timeout",
            desc = "The game main thread did not answer within 5 seconds")
    public StateSnapshot state() {
        return game.call(backend::snapshot);
    }

    @NmPost("/api/v1/macros/{id+}/{action}")
    @NmApi(tag = "macros", summary = "Run one macro lifecycle action (enable, disable,"
            + " start, stop, reload)")
    @NmError(status = 404, code = "not_found", desc = "Unknown macro or action")
    @NmError(status = 409, code = "conflict",
            desc = "The action conflicts with the macro's current state")
    @NmError(status = 504, code = "game_thread_timeout",
            desc = "The game main thread did not answer within 5 seconds")
    public MacroView macroAction(
            @NmParam(value = "id", multi = true)
            @NmDesc("The macro id category/name (may contain slashes)") String id,
            @NmParam("action") @NmDesc("One of enable, disable, start, stop, reload")
            String action) {
        return game.call(() -> backend.macroAction(id, action));
    }

    @NmGet("/api/v1/tasks")
    @NmApi(tag = "tasks", summary = "Task manager snapshot (active tasks + bounded history)")
    @NmError(status = 401, code = "unauthorized",
            desc = "Request unsigned or signature verification failed")
    public TasksResponse tasks() {
        return backend.tasks();
    }

    @NmPost("/api/v1/tasks/{id+}/stop")
    @NmApi(tag = "tasks", summary = "Stop one task (unloads its macro)")
    @NmError(status = 404, code = "not_found", desc = "Unknown task id")
    @NmError(status = 409, code = "conflict", desc = "The task is already stopped")
    @NmError(status = 504, code = "game_thread_timeout",
            desc = "The game main thread did not answer within 5 seconds")
    public TasksResponse stopTask(
            @NmParam(value = "id", multi = true)
            @NmDesc("The task id") long id) {
        return game.call(() -> {
            backend.stopTask(id);
            return backend.tasks();
        });
    }

    @NmGet("/api/v1/macros/{id+}/files")
    @NmApi(tag = "macros", summary = "List one macro folder's files (recursive,"
            + " folder-relative POSIX paths, the manifest first)")
    @NmError(status = 403, code = "forbidden",
            desc = "The macro folder escapes the macros root")
    @NmError(status = 404, code = "not_found", desc = "Unknown macro")
    public FilesResponse listFiles(
            @NmParam(value = "id", multi = true)
            @NmDesc("The macro id category/name (may contain slashes)") String id) {
        return backend.listFiles(id);
    }

    @NmPost("/api/v1/macros")
    @NmApi(tag = "macros", summary = "Create a macro scaffold (manifest + entry template)")
    @NmError(status = 409, code = "conflict",
            desc = "A macro with this name already exists (names are unique across categories)")
    @NmError(status = 422, code = "invalid_request",
            desc = "The category or name is invalid")
    @NmError(status = 504, code = "game_thread_timeout",
            desc = "The game main thread did not answer within 5 seconds")
    public MacroView createMacro(@NmBody CreateMacroRequest request) {
        return game.call(() -> backend.createMacro(
                request.category(), request.name(), request.description()));
    }

    @NmPost("/api/v1/macros/move")
    @NmApi(tag = "macros", summary = "Move one macro folder to another category (identity"
            + " and persisted state follow the manifest name)")
    @NmError(status = 404, code = "not_found", desc = "Unknown macro")
    @NmError(status = 409, code = "conflict", desc = "A folder with this name exists in the target category")
    @NmError(status = 422, code = "invalid_request", desc = "The target is not a valid category")
    @NmError(status = 504, code = "game_thread_timeout",
            desc = "The game main thread did not answer within 5 seconds")
    public MacroView moveMacro(@NmBody MoveMacroRequest request) {
        if (request.from() == null || request.from().isBlank()
                || request.to() == null || request.to().isBlank()) {
            throw ApiError.unprocessable("the move request needs from and to");
        }
        return game.call(() -> backend.moveMacro(request.from(), request.to()));
    }

    @NmDelete("/api/v1/macros/{id+}")
    @NmApi(tag = "macros", summary = "Delete one macro (folder, instance, persisted state)")
    @NmError(status = 404, code = "not_found", desc = "Unknown macro")
    @NmError(status = 422, code = "invalid_request",
            desc = "The body must be {\"confirm\":true}")
    @NmError(status = 504, code = "game_thread_timeout",
            desc = "The game main thread did not answer within 5 seconds")
    public void deleteMacro(
            @NmParam(value = "id", multi = true)
            @NmDesc("The macro id category/name (may contain slashes)") String id,
            ApiContext context) {
        if (!confirmed(context)) {
            throw ApiError.unprocessable("macro deletion needs a body of {\"confirm\":true}");
        }
        game.call(() -> {
            backend.deleteMacro(id);
            return null;
        });
    }

    @NmGet("/api/v1/files/{path+}")
    @NmApi(tag = "files", summary = "Read one file under the macros root",
            success = 200, produces = "text/plain; charset=utf-8")
    @NmError(status = 403, code = "forbidden", desc = "The path escapes the macros root")
    @NmError(status = 404, code = "not_found", desc = "No such file")
    @NmError(status = 413, code = "invalid_request", desc = "The file exceeds the read cap")
    public void readFile(
            @NmParam(value = "path", multi = true)
            @NmDesc("The macros-root-relative path (may contain slashes)") String path,
            ApiContext context) throws IOException {
        FileContent file = backend.readFile(path);
        context.exchange().getResponseHeaders().set("ETag", file.etag());
        ApiContext.respond(context.exchange(), 200, "text/plain; charset=utf-8", file.bytes());
        context.markHandled();
    }

    @NmPut("/api/v1/files/{path+}")
    @NmApi(tag = "files", summary = "Write one file (If-Match concurrency; save hot-reloads"
            + " the owning macro)", consumes = "text/plain; charset=utf-8")
    @NmError(status = 409, code = "conflict",
            desc = "If-Match mismatch; the body carries the current text and etag")
    @NmError(status = 403, code = "forbidden", desc = "The path escapes the macros root")
    @NmError(status = 404, code = "not_found", desc = "No such macro owns the path")
    @NmError(status = 413, code = "invalid_request", desc = "The body exceeds 1 MiB")
    @NmError(status = 422, code = "invalid_request",
            desc = "The file extension is outside the text whitelist")
    @NmError(status = 504, code = "game_thread_timeout",
            desc = "The game main thread did not answer within 5 seconds")
    public FileSaveResult writeFile(
            @NmParam(value = "path", multi = true)
            @NmDesc("The macros-root-relative path (may contain slashes)") String path,
            ApiContext context) throws IOException {
        String ifMatch = context.header("If-Match");
        FileWriteResult result = game.call(() -> backend.writeFile(path, context.body(), ifMatch));
        if (result.conflict()) {
            ApiError error = ApiError.conflict(
                    "the file changed on disk; the body carries the current text and etag");
            com.google.gson.JsonObject body = Jsons.valueOf(
                    ApiErrorBody.of(error)).getAsJsonObject();
            body.addProperty("content", result.conflictContent());
            body.addProperty("etag", result.currentEtag());
            ApiContext.respond(context.exchange(), error.status(),
                    "application/json; charset=utf-8",
                    Jsons.stringify(body).getBytes(StandardCharsets.UTF_8));
            context.markHandled();
            return null;
        }
        return new FileSaveResult(result.error());
    }

    @NmDelete("/api/v1/files/{path+}")
    @NmApi(tag = "files", summary = "Delete one file or directory tree (confined to"
            + " macro folders; manifests and macro folders are protected)")
    @NmError(status = 403, code = "forbidden",
            desc = "The path escapes the macros root, leaves any macro folder,"
                    + " or hits a protected manifest")
    @NmError(status = 404, code = "not_found", desc = "No such file")
    @NmError(status = 422, code = "invalid_request",
            desc = "The body must be {\"confirm\":true}")
    @NmError(status = 504, code = "game_thread_timeout",
            desc = "The game main thread did not answer within 5 seconds")
    public void deleteFile(
            @NmParam(value = "path", multi = true)
            @NmDesc("The macros-root-relative path (may contain slashes)") String path,
            ApiContext context) {
        if (!confirmed(context)) {
            throw ApiError.unprocessable(
                    "file deletion needs a body of {\"confirm\":true}");
        }
        game.call(() -> {
            backend.deleteFile(path);
            return null;
        });
    }

    @NmPost("/api/v1/files/move")
    @NmApi(tag = "files", summary = "Move or rename one file or directory within its"
            + " macro folder")
    @NmError(status = 403, code = "forbidden",
            desc = "A path escapes the macros root, leaves the macro folder, or"
                    + " hits a protected manifest")
    @NmError(status = 404, code = "not_found", desc = "The source does not exist")
    @NmError(status = 409, code = "conflict", desc = "The target already exists")
    @NmError(status = 422, code = "invalid_request",
            desc = "The target extension is outside the text whitelist, contains"
                    + " whitespace, or a directory would move into itself")
    @NmError(status = 504, code = "game_thread_timeout",
            desc = "The game main thread did not answer within 5 seconds")
    public void moveFile(@NmBody MoveRequest request) {
        if (request.from() == null || request.from().isBlank()
                || request.to() == null || request.to().isBlank()) {
            throw ApiError.unprocessable("the move request needs from and to paths");
        }
        game.call(() -> {
            backend.moveFile(request.from(), request.to());
            return null;
        });
    }

    @NmPost("/api/v1/files/mkdir")
    @NmApi(tag = "files", summary = "Create a directory (with parents) inside one"
            + " macro folder")
    @NmError(status = 403, code = "forbidden",
            desc = "The path escapes the macros root or lies outside any macro folder")
    @NmError(status = 409, code = "conflict", desc = "The directory already exists")
    @NmError(status = 422, code = "invalid_request",
            desc = "The path contains whitespace")
    public void makeDirectory(@NmBody MkdirRequest request) {
        if (request.path() == null || request.path().isBlank()) {
            throw ApiError.unprocessable("the mkdir request needs a path");
        }
        backend.makeDirectory(request.path());
    }

    @NmPost("/api/v1/files/reveal")
    @NmApi(tag = "files", summary = "Reveal one path in the system file manager of the"
            + " machine running the game")
    @NmError(status = 403, code = "forbidden", desc = "The path escapes the macros root")
    @NmError(status = 404, code = "not_found", desc = "No such file or directory")
    @NmError(status = 422, code = "invalid_request",
            desc = "The path contains backslashes, dot-dot, empty segments, or whitespace")
    public void reveal(@NmBody RevealRequest request) {
        if (request.path() == null || request.path().isBlank()) {
            throw ApiError.unprocessable("the reveal request needs a path");
        }
        backend.reveal(request.path());
    }

    @NmGet("/api/v1/devices")
    @NmApi(tag = "devices", summary = "The paired web devices (same list as the in-game"
            + " devices command)")
    public DevicesResponse listDevices() {
        List<DeviceView> views = devices.all().stream()
                .map(device -> new DeviceView(device.id(), device.label(), device.algo(),
                        device.createdAt(), device.lastSeenAt()))
                .toList();
        return new DevicesResponse(views);
    }

    @NmDelete("/api/v1/devices/{id}")
    @NmApi(tag = "devices", summary = "Revoke one paired device (its next request fails"
            + " authentication)")
    @NmError(status = 404, code = "not_found", desc = "Unknown device id")
    public void revokeDevice(@NmParam("id") @NmDesc("The device id") String id) {
        if (!devices.revoke(id)) {
            throw ApiError.notFound("unknown device: " + id);
        }
    }

    /** Whether the request body is exactly a confirm:true JSON object. */
    private static boolean confirmed(ApiContext context) {
        String text = new String(context.body(), StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return false;
        }
        try {
            var json = Jsons.parse(text);
            return json.isJsonObject()
                    && Boolean.TRUE.equals(Jsons.plain(json.getAsJsonObject().get("confirm")));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @NmGet("/api/v1/logs")
    @NmApi(tag = "logs", summary = "Replay the log ring")
    public LogsResponse logs(
            @NmParam("after") @NmDesc("Return entries with a seq greater than this;"
                    + " 0 replays from the start") Long after) {
        long cursor = after == null ? 0 : after;
        List<LogEntry> view = logs.after(cursor).stream()
                .map(entry -> new LogEntry(entry.seq(), entry.ts(), entry.level(),
                        entry.logger(), entry.line()))
                .toList();
        return new LogsResponse(view, logs.lastSeq());
    }

    @NmGet("/api/v1/console")
    @NmApi(tag = "console", summary = "Replay the script console ring")
    public ConsoleResponse console(
            @NmParam("after") @NmDesc("Return entries with a seq greater than this;"
                    + " 0 replays from the start") Long after) {
        long cursor = after == null ? 0 : after;
        return new ConsoleResponse(console.after(cursor), console.lastSeq());
    }

    @NmGet("/api/v1/network")
    @NmApi(tag = "network", summary = "Replay the recorded neo.net request ring")
    public NetworkResponse network(
            @NmParam("after") @NmDesc("Return entries with a seq greater than this;"
                    + " 0 replays from the start") Long after) {
        long cursor = after == null ? 0 : after;
        return new NetworkResponse(net.after(cursor), net.lastSeq());
    }

    @NmGet("/api/v1/network/{seq}/body")
    @NmApi(tag = "network", summary = "Fetch one retained request or response body"
            + " (side defaults to \"response\"; served with the entry's content"
            + " type and an attachment disposition)")
    @NmError(status = 404, code = "not_found", desc = "Unknown network entry seq")
    @NmError(status = 410, code = "body_evicted",
            desc = "The body was oversize when recorded or already evicted"
                    + " from the shared retention budget")
    public void networkBody(
            @NmParam("seq") @NmDesc("The network entry seq") Long seq,
            @NmParam("side") @NmDesc("\"request\" or \"response\" (default)") String side,
            ApiContext context) throws IOException {
        NetworkEntry entry = seq == null ? null : net.entry(seq);
        if (entry == null) {
            throw ApiError.notFound("unknown network entry seq");
        }
        boolean requestSide = "request".equals(side);
        byte[] payload;
        String contentType;
        String filename;
        if (requestSide) {
            String body = net.requestBody(seq);
            if (body == null) {
                throw new ApiError(410, "body_evicted",
                        "the request body was oversize when recorded or already evicted");
            }
            payload = body.getBytes(StandardCharsets.UTF_8);
            contentType = headerOr(entry.requestHeaders(), "content-type",
                    "text/plain; charset=utf-8");
            filename = bodyFilename(entry, seq, "request");
        } else {
            byte[] body = net.responseBody(seq);
            if (body == null) {
                throw new ApiError(410, "body_evicted",
                        "the response body was oversize when recorded or already evicted");
            }
            payload = body;
            contentType = headerOr(entry.responseHeaders(), "content-type",
                    "application/octet-stream");
            filename = bodyFilename(entry, seq, "response");
        }
        HttpExchange exchange = context.exchange();
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        ApiContext.respond(exchange, 200, contentType, payload);
        context.markHandled();
    }

    /** One header from an entry's recorded map, lower-cased, or the fallback. */
    private static String headerOr(Map<String, String> headers, String name,
            String fallback) {
        if (headers == null) {
            return fallback;
        }
        String value = headers.get(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** The URL path's last segment (query stripped), or a stable fallback. */
    private static String bodyFilename(NetworkEntry entry, long seq, String side) {
        String url = entry.url();
        String path = url != null ? url.split("\\?", 2)[0] : "";
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.isBlank()) {
            name = side + "-" + seq;
        }
        return name;
    }

    /** The pseudo macro name of the shared devtools session. */
    private static final String DEVTOOLS_TARGET = "devtools";

    @NmPost("/api/v1/console/eval")
    @NmApi(tag = "console", summary = "Evaluate one DevTools console expression"
            + " in the shared devtools session (target \"devtools\") or in a"
            + " loaded macro's context; the echoed input and the result are"
            + " recorded into the console ring so they replay across reconnects")
    @NmError(status = 400, code = "invalid_request",
            desc = "Malformed body (needs a non-empty expression)")
    @NmError(status = 404, code = "not_found", desc = "The target macro is not loaded")
    @NmError(status = 504, code = "eval_timeout",
            desc = "The expression did not settle within the context loop budget")
    public ConsoleResponse evalConsole(@NmBody EvalRequest request) {
        if (request.expression() == null || request.expression().isBlank()) {
            throw ApiError.invalidRequest("the eval request needs a non-empty expression");
        }
        String target = request.target() == null || request.target().isBlank()
                ? DEVTOOLS_TARGET
                : request.target();
        PanelBackend.EvalExecution execution = backend.evaluateConsole(target, request.expression());
        long ts = System.currentTimeMillis();
        ConsoleEntry input = console.append(new ScriptConsoleEvent(
                execution.macro(), "input", "log", null, null, null, 0,
                List.of(strNode(request.expression())), null, null, null, null, ts));
        ConsoleEntry output = console.append(new ScriptConsoleEvent(
                execution.macro(), "result",
                execution.outcome().threw() ? "error" : "info",
                null, null, null, 0,
                List.of(execution.outcome().threw()
                        ? errorNode(execution.outcome())
                        : execution.outcome().value()),
                null, null, null, null, ts));
        return new ConsoleResponse(List.of(input, output), output.seq());
    }

    /** One neutral string node for the echoed input. */
    private static Map<String, Object> strNode(String value) {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("t", "str");
        node.put("v", value);
        return node;
    }

    /** One neutral error node from an eval outcome. */
    private static Map<String, Object> errorNode(ink.tuanzi.neomacros.core.runtime.EvalOutcome outcome) {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("t", "err");
        node.put("name", outcome.errorName() == null ? "Error" : outcome.errorName());
        node.put("msg", outcome.errorMessage() == null ? "" : outcome.errorMessage());
        node.put("stack", outcome.errorStack() == null ? "" : outcome.errorStack());
        return node;
    }

    @NmPost("/api/v1/events/ticket")
    @NmApi(tag = "events", summary = "Exchange a signature for a one-time SSE ticket")
    public TicketResponse ticket() {
        return new TicketResponse(sse.issueTicket(),
                (int) (SseHub.TICKET_TTL_MILLIS / 1000));
    }

    @NmGet("/api/v1/events")
    @NmApi(tag = "events", summary = "The SSE event stream", auth = NmAuth.TICKET, success = 200)
    @NmError(status = 401, code = "unauthorized",
            desc = "Ticket missing, expired, or already used")
    public void events(ApiContext context) throws IOException {
        HttpExchange exchange = context.exchange();
        if (!sse.consumeTicket(context.queryParam("ticket"))) {
            byte[] payload = Jsons.stringify(ApiErrorBody.of(
                            ApiError.unauthorized("ticket missing, expired, or already used")))
                    .getBytes(StandardCharsets.UTF_8);
            ApiContext.respond(exchange, 401, "application/json; charset=utf-8", payload);
            context.markHandled();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        sse.open(exchange.getResponseBody(), context.source());
        context.markHandled();
        // The hub broadcasts from here on; a connection drops itself on the
        // first failed write (client gone) or when the server shuts down.
    }
}
