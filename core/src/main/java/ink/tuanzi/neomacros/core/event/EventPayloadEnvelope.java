package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.doc.api.ApiEvent;

import java.util.List;

/**
 * Transport wrapper pairing one event payload with its concrete name, traffic
 * direction, and delivery channel. The event bus wraps every dispatched
 * payload so the engine boundary can attach the {@code XxxEvent extends
 * BaseEvent} prototype without losing the name context (wildcard
 * subscriptions make subscription-time names unreliable).
 *
 * <p>Sync-channel envelopes additionally carry the mutable-field whitelist,
 * the read-only flag (MONITOR priority), and the hooks sink that the engine
 * binds to the mutable event face; writes committed through the hooks merge
 * back onto the event state after each callback.</p>
 */
public final class EventPayloadEnvelope {

    private final String eventName;
    private final boolean cancellable;
    private final ApiEvent.Direction direction;
    private final long timestamp;
    private final Object payload;
    private final boolean syncChannel;
    private final List<String> mutableKeys;
    private final boolean readOnly;
    private final SyncEventHooks hooks;

    /** Async-channel envelope: the frozen snapshot face. */
    public EventPayloadEnvelope(String eventName, boolean cancellable,
                                ApiEvent.Direction direction, long timestamp, Object payload) {
        this(eventName, cancellable, direction, timestamp, payload, false, List.of(), false, null);
    }

    /** Full envelope; sync-channel envelopes carry the mutable-face metadata and hooks sink. */
    public EventPayloadEnvelope(String eventName, boolean cancellable,
                                ApiEvent.Direction direction, long timestamp, Object payload,
                                boolean syncChannel, List<String> mutableKeys, boolean readOnly,
                                SyncEventHooks hooks) {
        this.eventName = eventName;
        this.cancellable = cancellable;
        this.direction = direction;
        this.timestamp = timestamp;
        this.payload = payload;
        this.syncChannel = syncChannel;
        this.mutableKeys = List.copyOf(mutableKeys);
        this.readOnly = readOnly;
        this.hooks = hooks;
    }

    public String eventName() {
        return eventName;
    }

    public boolean cancellable() {
        return cancellable;
    }

    public ApiEvent.Direction direction() {
        return direction;
    }

    /** Publish-time epoch millis (host-stamped; wrap time never skews it). */
    public long timestamp() {
        return timestamp;
    }

    public Object payload() {
        return payload;
    }

    /** Whether this envelope rides the synchronous channel (mutable event face). */
    public boolean syncChannel() {
        return syncChannel;
    }

    /** Payload fields a synchronous subscriber may rewrite. */
    public List<String> mutableKeys() {
        return mutableKeys;
    }

    /** Whether this sync delivery is observation-only (MONITOR priority). */
    public boolean readOnly() {
        return readOnly;
    }

    /** The mutation sink of this sync delivery (null on the async channel). */
    public SyncEventHooks hooks() {
        return hooks;
    }
}
