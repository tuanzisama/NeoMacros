package ink.tuanzi.neomacros.core.security;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The single pending-decision table for script trust: every grant the player
 * can make — a first-load trust prompt and a runtime
 * {@code requestPermission} call alike — becomes one request here, surfaced
 * one prompt at a time (dialog on nodes with the vanilla dialog system, a
 * clickable chat line below) and settled by the player through the dialog
 * buttons or the {@code /neomacros permission} command. An allow persists
 * trust for the whole script (the same decision everywhere) and flips the
 * live view immediately; a deny also remembers the refusal for this session
 * (no further prompts for that content until restart); an ignore settles the
 * script's future as false and forgets.
 */
public final class PermissionRequests {

    /** The player's decision for one pending request. */
    public enum Decision {
        /** Grant: persists trust for the whole script, applied live. */
        GRANT,
        /** Deny: settles false and remembers the refusal for this session. */
        DENY,
        /** Ignore: settles false and remembers nothing (prompts again later). */
        IGNORE
    }

    /**
     * One pending trust decision. {@code capabilities} lists the specific
     * capabilities a script asked for at runtime (empty for a first-load
     * prompt, which covers the whole declared set); {@code awaited} marks a
     * runtime request whose script is suspended on the answer;
     * {@code description} is the script's manifest description (may be
     * empty), shown in the prompt UI.
     */
    public record PermissionRequest(String id, String scriptName, String contentHash,
                                    List<String> capabilities, List<String> declared,
                                    boolean awaited, String description) {
    }

    private record Pending(PermissionRequest request, CompletableFuture<Boolean> future) {
    }

    private final TrustManager trustManager;
    private final List<Consumer<PermissionRequest>> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final Deque<String> queue = new ConcurrentLinkedDeque<>();
    /** The request id currently surfaced to the UI (one prompt at a time). */
    private String showing;

    /** Creates the decision table over the trust gate it grants through. */
    public PermissionRequests(TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    /** Registers the listener that surfaces requests to the player (the UI layer). */
    public void onRequest(Consumer<PermissionRequest> listener) {
        listeners.add(listener);
    }

    /**
     * Submits one runtime request (awaited): queues it behind any prompt
     * already showing and returns the future the script awaits (true =
     * granted, false = denied, ignored, or cancelled). A script whose
     * content the player denied this session settles false immediately
     * without surfacing anything.
     */
    public CompletableFuture<Boolean> submit(PermissionRequest request) {
        if (trustManager.isSessionDenied(request.contentHash())) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        var future = new CompletableFuture<Boolean>();
        enqueue(new Pending(withAwaited(request, true), future));
        return future;
    }

    /**
     * Submits one first-load trust prompt (not awaited): same queue and
     * surfacing as a runtime request, but nothing waits on the answer. A
     * still-pending prompt for the same script is replaced so repeated loads
     * do not pile up duplicate dialogs; awaited requests of that script are
     * left untouched. A session-denied hash submits nothing.
     */
    public synchronized void submitLoad(String scriptName, String contentHash,
                                        String description, List<String> declared) {
        if (trustManager.isSessionDenied(contentHash)) {
            return;
        }
        for (var iterator = pending.values().iterator(); iterator.hasNext(); ) {
            Pending entry = iterator.next();
            if (!entry.request().awaited() && entry.request().scriptName().equals(scriptName)) {
                iterator.remove();
                queue.remove(entry.request().id());
                if (entry.request().id().equals(showing)) {
                    // The surfaced dialog is being superseded; the next
                    // drain surfaces the replacement.
                    showing = null;
                }
            }
        }
        enqueue(new Pending(new PermissionRequest(
                java.util.UUID.randomUUID().toString(), scriptName, contentHash,
                List.of(), List.copyOf(declared), false, description), null));
    }

    /**
     * Applies the player's decision to a target: an exact pending request id
     * (one entry) or a script name (every pending entry of that script). A
     * grant is script-wide — trust persists per content hash and every
     * sibling entry of the script settles true, queued or not. A deny
     * remembers the refusal for this session and settles the matched
     * entries false. An ignore settles them false and remembers nothing.
     * Returns the script name whose decisions were settled, or null when
     * nothing matched (unknown or already-settled target).
     */
    public synchronized String respond(String target, Decision decision) {
        List<Pending> matches = new ArrayList<>();
        Pending byId = pending.get(target);
        if (byId != null) {
            matches.add(byId);
            if (decision == Decision.GRANT) {
                // The grant covers the whole script: siblings are satisfied
                // by the same decision, wherever they sit in the queue.
                for (Pending entry : pending.values()) {
                    if (entry != byId
                            && entry.request().scriptName().equals(byId.request().scriptName())) {
                        matches.add(entry);
                    }
                }
            }
        } else {
            for (Pending entry : pending.values()) {
                if (entry.request().scriptName().equals(target)) {
                    matches.add(entry);
                }
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        if (decision == Decision.GRANT) {
            // Normally one hash per script; a mid-flight edit can leave the
            // prompt and the runtime request keyed differently, so grant
            // each distinct hash with its own declared set.
            var seen = new HashSet<String>();
            for (Pending entry : matches) {
                String hash = entry.request().contentHash();
                if (hash != null && seen.add(hash)) {
                    trustManager.trustLive(hash, new HashSet<>(entry.request().declared()));
                }
            }
        } else if (decision == Decision.DENY) {
            var seen = new HashSet<String>();
            for (Pending entry : matches) {
                String hash = entry.request().contentHash();
                if (hash != null && seen.add(hash)) {
                    trustManager.denySession(hash);
                }
            }
        }
        boolean granted = decision == Decision.GRANT;
        for (Pending entry : matches) {
            settle(entry, granted);
        }
        drain();
        return matches.get(0).request().scriptName();
    }

    /** Cancels every pending decision of one script (its unload): all settle false. */
    public synchronized void cancelFor(String scriptName) {
        for (var iterator = pending.values().iterator(); iterator.hasNext(); ) {
            Pending entry = iterator.next();
            if (entry.request().scriptName().equals(scriptName)) {
                iterator.remove();
                queue.remove(entry.request().id());
                if (entry.request().id().equals(showing)) {
                    showing = null;
                }
                settle(entry, false);
            }
        }
        drain();
    }

    /** Every currently pending decision (command suggestions, diagnostics). */
    public synchronized List<PermissionRequest> pending() {
        List<PermissionRequest> out = new ArrayList<>();
        for (Pending entry : pending.values()) {
            out.add(entry.request());
        }
        return List.copyOf(out);
    }

    /** Enqueues one entry and surfaces the next prompt when none is showing. */
    private synchronized void enqueue(Pending entry) {
        pending.put(entry.request().id(), entry);
        queue.addLast(entry.request().id());
        drain();
    }

    /** Removes a settled entry and completes its future when awaited. */
    private void settle(Pending entry, boolean granted) {
        pending.remove(entry.request().id());
        queue.remove(entry.request().id());
        if (entry.request().id().equals(showing)) {
            showing = null;
        }
        if (entry.future() != null) {
            entry.future().complete(granted);
        }
    }

    /** Surfaces the next queued request when no prompt is showing. */
    private void drain() {
        while (showing == null) {
            String id = queue.pollFirst();
            if (id == null) {
                return;
            }
            Pending entry = pending.get(id);
            if (entry == null) {
                continue; // settled while queued: surface the next one
            }
            showing = id;
            for (Consumer<PermissionRequest> listener : listeners) {
                listener.accept(entry.request());
            }
            return;
        }
    }

    private static PermissionRequest withAwaited(PermissionRequest request, boolean awaited) {
        if (request.awaited() == awaited) {
            return request;
        }
        return new PermissionRequest(request.id(), request.scriptName(), request.contentHash(),
                request.capabilities(), request.declared(), awaited, request.description());
    }
}
