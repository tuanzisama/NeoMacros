package ink.tuanzi.neomacros.fabric.web.sse;

/**
 * The service-lifetime broadcast facade: the hub lives and dies with each
 * {@code WebService.start()}, while game-side surfaces (the runtime backend,
 * the module listeners) outlive restarts. They broadcast through this handle;
 * with no listener attached the calls are no-ops.
 */
public final class PanelEvents {

    private volatile SseHub hub;

    /** Attaches the hub of a running service. */
    public synchronized void attach(SseHub hub) {
        this.hub = hub;
    }

    /** Detaches the hub (service stopped; live streams are already closed). */
    public synchronized void detach() {
        this.hub = null;
    }

    /** Broadcasts one named event with its JSON payload, when attached. */
    public void broadcast(String event, String jsonData) {
        SseHub current = hub;
        if (current != null) {
            current.broadcast(event, jsonData);
        }
    }
}
