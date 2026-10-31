package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.core.event.def.chat.ChatSendEvent;
import ink.tuanzi.neomacros.core.event.def.tick.TickEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synchronous cancellation channel semantics: onSync legality, cancellation
 * propagation, and the channel's separation from async dispatch.
 */
class EventBusSyncTest {

    private EventCatalog catalog;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        catalog = new EventCatalog();
        catalog.registerAll(java.util.List.of(ChatSendEvent.class, TickEvent.class));
        bus = new EventBus(catalog, Runnable::run, null);
    }

    @Test
    void onSyncRequiresKnownCancellableEvent() {
        // Unknown event.
        assertThrows(IllegalStateException.class, () -> bus.onSync("custom:nope", (p, f) -> {
        }));
        // Known but non-cancellable.
        assertThrows(IllegalStateException.class, () -> bus.onSync("tick", (p, f) -> {
        }));
        // Wildcards never allowed on the sync channel.
        assertThrows(IllegalArgumentException.class, () -> bus.onSync("chat:*", (p, f) -> {
        }));
        // The legal case subscribes.
        assertTrue(bus.onSync("chat:send", (p, f) -> {
        }).isActive());
    }

    @Test
    void cancellationPropagatesToPublishResult() {
        bus.onSync("chat:send", (payload, flag) -> flag.cancel());

        EventBus.PublishResult result = bus.publish("chat:send", new ChatSendEvent("hi"));
        assertTrue(result.canceled());
    }

    @Test
    void nonCanceledEventReportsFalse() {
        bus.onSync("chat:send", (payload, flag) -> {
            // Reads the flag without canceling.
            assertFalse(flag.isCanceled());
        });

        assertFalse(bus.publish("chat:send", new ChatSendEvent("hi")).canceled());
    }

    @Test
    void multipleSyncCallbacksRunInRegistrationOrder() {
        List<String> order = new java.util.ArrayList<>();
        bus.onSync("chat:send", (payload, flag) -> order.add("first"));
        bus.onSync("chat:send", (payload, flag) -> order.add("second"));
        bus.onSync("chat:send", (payload, flag) -> flag.cancel());

        EventBus.PublishResult result = bus.publish("chat:send", new ChatSendEvent("hi"));
        assertEquals(java.util.List.of("first", "second"), order);
        assertTrue(result.canceled());
        assertEquals(3, result.notified());
    }

    @Test
    void syncCallbackRunsOnDispatchThread() {
        String[] threadName = new String[1];
        bus.onSync("chat:send", (payload, flag) -> threadName[0] = Thread.currentThread().getName());
        bus.publish("chat:send", new ChatSendEvent("hi"));
        assertEquals(Thread.currentThread().getName(), threadName[0]);
    }

    @Test
    void payloadIsPassedThroughUnchanged() {
        ChatSendEvent event = new ChatSendEvent("hello");
        Object[] received = new Object[1];
        bus.onSync("chat:send", (payload, flag) -> received[0] = payload);
        bus.publish("chat:send", event);
        assertEquals(event, received[0]);
    }
}
