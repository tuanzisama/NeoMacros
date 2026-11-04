package ink.tuanzi.neomacros.core.event;

/**
 * Transport wrapper pairing one event payload with its concrete name and
 * cancellation support. The event bus wraps every dispatched payload so the
 * engine boundary can attach the {@code XxxEvent extends BaseEvent}
 * prototype without losing the name context (wildcard subscriptions make
 * subscription-time names unreliable).
 */
public final class EventPayloadEnvelope {

    private final String eventName;
    private final boolean cancellable;
    private final Object payload;

    public EventPayloadEnvelope(String eventName, boolean cancellable, Object payload) {
        this.eventName = eventName;
        this.cancellable = cancellable;
        this.payload = payload;
    }

    public String eventName() {
        return eventName;
    }

    public boolean cancellable() {
        return cancellable;
    }

    public Object payload() {
        return payload;
    }
}
