package ink.tuanzi.neomacros.fabric.web.v1;

import com.sun.net.httpserver.HttpExchange;
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
import ink.tuanzi.neomacros.fabric.web.http.ApiContext;
import ink.tuanzi.neomacros.fabric.web.http.ApiError;
import ink.tuanzi.neomacros.fabric.web.json.Jsons;
import ink.tuanzi.neomacros.fabric.web.log.LogRing;
import ink.tuanzi.neomacros.fabric.web.pair.PairingService;
import ink.tuanzi.neomacros.fabric.web.sse.SseHub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * The web panel API surface: version probe, pairing, state snapshot, macro
 * lifecycle actions with create/delete, the file editor API, device
 * management, log replay, and the SSE stream with its ticket exchange.
 * Every game-state read and macro action is dispatched onto the game main
 * thread with a five-second budget.
 */
public final class ApiV1 {

    private final PanelBackend backend;
    private final GameRunner game;
    private final PairingService pairing;
    private final DeviceRegistry devices;
    private final SseHub sse;
    private final LogRing logs;

    /** Creates the controller. */
    public ApiV1(PanelBackend backend, GameRunner game, PairingService pairing,
                 DeviceRegistry devices, SseHub sse, LogRing logs) {
        this.backend = backend;
        this.game = game;
        this.pairing = pairing;
        this.devices = devices;
        this.sse = sse;
        this.logs = logs;
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
            + " (single use, three minutes)", auth = NmAuth.NONE)
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
                .map(entry -> new LogEntry(entry.seq(), entry.level(), entry.logger(),
                        entry.line()))
                .toList();
        return new LogsResponse(view, logs.lastSeq());
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
