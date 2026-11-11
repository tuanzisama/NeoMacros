package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.core.event.def.chat.ChatSendEvent;
import ink.tuanzi.neomacros.core.event.def.chat.ChatTitleEvent;
import ink.tuanzi.neomacros.core.event.def.tick.TickEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synchronous cancellation channel semantics: onSync legality, last-write-wins
 * cancellation, priority ordering, sequential mutation visibility, and the
 * channel's separation from async dispatch.
 */
class EventBusSyncTest {

    private EventCatalog catalog;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        catalog = new EventCatalog();
        catalog.registerAll(java.util.List.of(ChatSendEvent.class, TickEvent.class,
                ChatTitleEvent.class));
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
    void lastWriteWinsAllowsUndoingAnEarlierCancel() {
        bus.onSync("chat:send", EventPriority.LOWEST, false, (payload, flag) -> flag.cancel());
        bus.onSync("chat:send", EventPriority.HIGHEST, false,
                (payload, flag) -> flag.setCanceled(false));

        // The later priority runs after the cancel and undoes it.
        assertFalse(bus.publish("chat:send", new ChatSendEvent("hi")).canceled());

        // Flipping the priorities restores one-way cancellation.
        EventCatalog fresh = new EventCatalog();
        fresh.registerAll(List.of(ChatSendEvent.class));
        EventBus other = new EventBus(fresh, Runnable::run, null);
        other.onSync("chat:send", EventPriority.HIGHEST, false, (payload, flag) -> flag.cancel());
        other.onSync("chat:send", EventPriority.LOWEST, false,
                (payload, flag) -> flag.setCanceled(false));
        assertTrue(other.publish("chat:send", new ChatSendEvent("hi")).canceled());
    }

    @Test
    void ignoreCancelledSkipsCallbacksOnceCanceled() {
        List<String> order = new java.util.ArrayList<>();
        bus.onSync("chat:send", EventPriority.LOWEST, false, (payload, flag) -> flag.cancel());
        bus.onSync("chat:send", EventPriority.NORMAL, false,
                (payload, flag) -> order.add("sees-cancel"));
        bus.onSync("chat:send", EventPriority.HIGH, true,
                (payload, flag) -> order.add("ignores-cancel"));

        EventBus.PublishResult result = bus.publish("chat:send", new ChatSendEvent("hi"));
        assertEquals(List.of("sees-cancel"), order);
        assertTrue(result.canceled());
        assertEquals(2, result.notified());
    }

    @Test
    void prioritiesRunLowestFirstHighestLast() {
        List<String> order = new java.util.ArrayList<>();
        bus.onSync("chat:send", EventPriority.HIGH, false, (payload, flag) -> order.add("high"));
        bus.onSync("chat:send", EventPriority.LOWEST, false, (payload, flag) -> order.add("lowest"));
        bus.onSync("chat:send", EventPriority.MONITOR, false, (payload, flag) -> order.add("monitor"));
        bus.onSync("chat:send", EventPriority.NORMAL, false, (payload, flag) -> order.add("normal"));

        bus.publish("chat:send", new ChatSendEvent("hi"));
        assertEquals(List.of("lowest", "normal", "high", "monitor"), order);
    }

    @Test
    void samePriorityTiesRunInRegistrationOrder() {
        List<String> order = new java.util.ArrayList<>();
        bus.onSync("chat:send", EventPriority.NORMAL, false, (payload, flag) -> order.add("first"));
        bus.onSync("chat:send", EventPriority.NORMAL, false, (payload, flag) -> order.add("second"));

        bus.publish("chat:send", new ChatSendEvent("hi"));
        assertEquals(List.of("first", "second"), order);
    }

    @Test
    void syncCallbackRunsOnDispatchThread() {
        String[] threadName = new String[1];
        bus.onSync("chat:send", (payload, flag) ->
                threadName[0] = Thread.currentThread().getName());
        bus.publish("chat:send", new ChatSendEvent("hi"));
        assertEquals(Thread.currentThread().getName(), threadName[0]);
    }

    @Test
    void syncDeliveryCarriesTheMutableFaceMetadata() {
        EventPayloadEnvelope[] received = new EventPayloadEnvelope[1];
        bus.onSync("chat:send", (payload, flag) ->
                received[0] = (EventPayloadEnvelope) payload);

        bus.publish("chat:send", new ChatSendEvent("hi"));
        EventPayloadEnvelope envelope = received[0];
        assertTrue(envelope.syncChannel());
        assertEquals(ink.tuanzi.neomacros.doc.api.ApiEvent.Direction.C2S, envelope.direction());
        assertEquals(List.of("message"), envelope.mutableKeys());
        assertFalse(envelope.readOnly());
        assertTrue(envelope.hooks() != null);
        // Sync payload is the shared state snapshot, not the raw record.
        assertTrue(envelope.payload() instanceof Map<?, ?> map
                && "hi".equals(map.get("message")));
    }

    @Test
    void monitorDeliveryIsFlaggedReadOnly() {
        EventPayloadEnvelope[] received = new EventPayloadEnvelope[1];
        bus.onSync("chat:send", EventPriority.MONITOR, false,
                (payload, flag) -> received[0] = (EventPayloadEnvelope) payload);

        bus.publish("chat:send", new ChatSendEvent("hi"));
        assertTrue(received[0].readOnly());
    }

    @Test
    void hookWritesAccumulateIntoTheVerdict() {
        bus.onSync("chat:send", (payload, flag) -> {
            EventPayloadEnvelope envelope = (EventPayloadEnvelope) payload;
            envelope.hooks().write("message", "rewritten");
        });

        EventBus.PublishResult result = bus.publish("chat:send", new ChatSendEvent("hi"));
        assertFalse(result.canceled());
        assertEquals(Map.of("message", "rewritten"), result.verdict());
    }

    @Test
    void laterPrioritiesObserveEarlierWrites() {
        List<Object> seen = new java.util.ArrayList<>();
        bus.onSync("chat:send", EventPriority.LOWEST, false, (payload, flag) ->
                ((EventPayloadEnvelope) payload).hooks().write("message", "from-lowest"));
        bus.onSync("chat:send", EventPriority.HIGHEST, false, (payload, flag) -> {
            Map<?, ?> state = (Map<?, ?>) ((EventPayloadEnvelope) payload).payload();
            seen.add(state.get("message"));
        });

        EventBus.PublishResult result = bus.publish("chat:send", new ChatSendEvent("hi"));
        assertEquals(List.of("from-lowest"), seen);
        assertEquals(Map.of("message", "from-lowest"), result.verdict());
    }

    @Test
    void lastWriteOverridesEarlierMutationFields() {
        bus.onSync("chat:send", EventPriority.LOWEST, false, (payload, flag) ->
                ((EventPayloadEnvelope) payload).hooks().write("message", "first"));
        bus.onSync("chat:send", EventPriority.HIGHEST, false, (payload, flag) ->
                ((EventPayloadEnvelope) payload).hooks().write("message", "second"));

        EventBus.PublishResult result = bus.publish("chat:send", new ChatSendEvent("hi"));
        assertEquals(Map.of("message", "second"), result.verdict());
    }

    @Test
    void unmutatedEventsCarryNoVerdict() {
        bus.onSync("chat:send", (payload, flag) -> {
        });

        EventBus.PublishResult result = bus.publish("chat:send", new ChatSendEvent("hi"));
        assertFalse(result.canceled());
        assertNull(result.verdict());
    }

    @Test
    void nonCancellablePublishCarriesNoVerdict() {
        bus.on("tick", payload -> {
        });

        EventBus.PublishResult result = bus.publish("tick", new TickEvent(1));
        assertFalse(result.canceled());
        assertNull(result.verdict());
    }

    @Test
    void asyncDispatchObservesTheFinalMutatedState() {
        bus.onSync("chat:send", (payload, flag) ->
                ((EventPayloadEnvelope) payload).hooks().write("message", "mutated"));
        List<Object> asyncSeen = new java.util.ArrayList<>();
        bus.on("chat:send", payload -> {
            EventPayloadEnvelope envelope = (EventPayloadEnvelope) payload;
            asyncSeen.add(((Map<?, ?>) envelope.payload()).get("message"));
        });

        bus.publish("chat:send", new ChatSendEvent("hi"));
        assertEquals(List.of("mutated"), asyncSeen);
    }
}
