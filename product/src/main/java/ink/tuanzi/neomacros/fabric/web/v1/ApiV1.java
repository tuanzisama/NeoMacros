package ink.tuanzi.neomacros.fabric.web.v1;

import com.sun.net.httpserver.HttpExchange;
import ink.tuanzi.neomacros.fabric.web.GameRunner;
import ink.tuanzi.neomacros.fabric.web.PanelBackend;
import ink.tuanzi.neomacros.fabric.web.api.NmApi;
import ink.tuanzi.neomacros.fabric.web.api.NmAuth;
import ink.tuanzi.neomacros.fabric.web.api.NmBody;
import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import ink.tuanzi.neomacros.fabric.web.api.NmError;
import ink.tuanzi.neomacros.fabric.web.api.NmGet;
import ink.tuanzi.neomacros.fabric.web.api.NmParam;
import ink.tuanzi.neomacros.fabric.web.api.NmPost;
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
 * lifecycle actions, log replay, and the SSE stream with its ticket
 * exchange. Every game-state read and macro action is dispatched onto the
 * game main thread with a five-second budget.
 */
public final class ApiV1 {

    private final PanelBackend backend;
    private final GameRunner game;
    private final PairingService pairing;
    private final DeviceRegistry devices;
    private final SseHub sse;
    private final LogRing logs;
    private final java.util.function.BiConsumer<String, String> pairingPrompt;

    /**
     * Creates the controller; {@code pairingPrompt} surfaces a pending
     * pairing to the player (the in-game approve card) with the request's
     * label and its confirmation code (null in token mode).
     */
    public ApiV1(PanelBackend backend, GameRunner game, PairingService pairing,
                 DeviceRegistry devices, SseHub sse, LogRing logs,
                 java.util.function.BiConsumer<String, String> pairingPrompt) {
        this.backend = backend;
        this.game = game;
        this.pairing = pairing;
        this.devices = devices;
        this.sse = sse;
        this.logs = logs;
        this.pairingPrompt = pairingPrompt;
    }

    @NmGet("/api/v1/meta")
    @NmApi(tag = "meta", summary = "Version negotiation probe (unsigned)", auth = NmAuth.NONE)
    @NmError(status = 403, code = "forbidden",
            desc = "The Host header is outside the allowlist (rebinding defense)")
    public Meta meta() {
        return backend.meta();
    }

    @NmPost("/api/v1/pair")
    @NmApi(tag = "pairing", summary = "Pair a device (long poll for the in-game decision)",
            auth = NmAuth.NONE)
    @NmError(status = 400, code = "invalid_request",
            desc = "Malformed request, an invalid or consumed pairing token, or a"
                    + " bad confirmation code")
    @NmError(status = 403, code = "forbidden", desc = "The pairing was denied in game")
    @NmError(status = 408, code = "timeout",
            desc = "No in-game decision within 120 seconds")
    public PairResponse pair(@NmBody PairRequest request) {
        boolean hasToken = request.token() != null && !request.token().isBlank();
        boolean hasCode = request.code() != null && !request.code().isBlank();
        if (request.label() == null || request.label().isBlank()
                || request.algo() == null || request.algo().isBlank()
                || request.pubJwk() == null || request.pubJwk().isEmpty()
                || hasToken == hasCode) {
            throw ApiError.invalidRequest(
                    "the pairing request needs label, algo, pubJwk, and exactly one"
                            + " of token or code");
        }
        PairingService.PendingRequest pending;
        if (hasCode) {
            if (!request.code().matches("[A-Z0-9]{6}")) {
                throw ApiError.invalidRequest(
                        "the confirmation code must be six uppercase letters or digits");
            }
            pending = pairing.submitWithCode(request.code(), request.label(),
                    request.algo(), request.pubJwk());
        } else {
            pending = pairing.submit(request.token(), request.label(), request.algo(),
                    request.pubJwk());
            if (pending == null) {
                throw ApiError.invalidRequest(
                        "invalid, expired, or already-consumed pairing token");
            }
        }
        pairingPrompt.accept(request.label(), hasCode ? request.code() : null);
        try {
            PairingService.Decision decision = pending.resolution()
                    .get(pairing.decisionTimeoutMillis() + 5_000, TimeUnit.MILLISECONDS);
            return switch (decision) {
                case APPROVED -> {
                    DeviceRecord device = devices.register(
                            request.label(), request.algo(), request.pubJwk());
                    yield new PairResponse(device.id());
                }
                case DENIED -> throw new ApiError(403, ApiError.FORBIDDEN,
                        "the pairing request was denied in game");
                case TIMED_OUT, REPLACED -> throw new ApiError(408, ApiError.TIMEOUT,
                        "the in-game pairing decision timed out; generate a new link");
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiError(500, ApiError.INTERNAL, "interrupted");
        } catch (TimeoutException | ExecutionException e) {
            throw new ApiError(408, ApiError.TIMEOUT,
                    "the in-game pairing decision timed out; generate a new link");
        }
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
