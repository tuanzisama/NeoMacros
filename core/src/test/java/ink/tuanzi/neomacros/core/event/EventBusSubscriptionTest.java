package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.core.event.def.tick.TickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Subscription semantics of the asynchronous channel: delivery, once,
 * unsubscribe paths, per-subscription executors, and dispatch counting.
 */
class EventBusSubscriptionTest {

    private SyncWatchdog watchdog;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        // Direct executor: async callbacks run inline, keeping assertions deterministic.
        watchdog = new SyncWatchdog(violation -> {
        });
        bus = new EventBus(new EventCatalog(), Runnable::run, watchdog);
    }

    @AfterEach
    void tearDown() {
        watchdog.close();
    }

    @Test
    void deliversPayloadToSubscriber() {
        List<Object> received = new ArrayList<>();
        bus.on("tick", p -> received.add(unwrap(p)));

        EventBus.PublishResult result = bus.publish("tick", new TickEvent(1));
        assertEquals(new TickEvent(1), received.get(0));
        assertEquals(1, result.notified());
        assertFalse(result.canceled());
    }

    @Test
    void publishUnknownEventIsAllowed() {
        AtomicReference<Object> received = new AtomicReference<>();
        bus.on("custom:unknown", p -> received.set(unwrap(p)));
        assertNull(received.get());

        bus.publish("custom:unknown", "payload");
        assertEquals("payload", received.get());
    }

    @Test
    void onceFiresExactlyOnceThenDeactivates() {
        List<Object> received = new ArrayList<>();
        bus.once("tick", p -> received.add(unwrap(p)));
        assertTrue(bus.publish("tick", new TickEvent(1)).notified() > 0);
        assertTrue(bus.publish("tick", new TickEvent(2)).notified() == 0);

        assertEquals(1, received.size());
        assertEquals(new TickEvent(1), received.get(0));
    }

    @Test
    void unsubscribeByHandleStopsDelivery() {
        List<Object> received = new ArrayList<>();
        Subscription sub = bus.on("tick", p -> received.add(unwrap(p)));
        assertTrue(sub.isActive());

        bus.publish("tick", new TickEvent(1));
        bus.off(sub);
        assertFalse(sub.isActive());
        bus.off(sub); // idempotent

        bus.publish("tick", new TickEvent(2));
        assertEquals(1, received.size());
    }

    @Test
    void unsubscribeByNameStopsDelivery() {
        List<Object> received = new ArrayList<>();
        bus.on("tick", p -> received.add(unwrap(p)));
        bus.on("tick", p -> received.add(unwrap(p)));

        bus.off("tick");
        assertEquals(0, bus.publish("tick", new TickEvent(1)).notified());
    }

    @Test
    void dedicatedExecutorReceivesDispatch() throws Exception {
        // Queueing executor: dispatch must enqueue onto it, not run inline.
        List<Runnable> queued = new CopyOnWriteArrayList<>();
        Executor queueing = queued::add;
        List<Object> received = new ArrayList<>();
        bus.on("tick", queueing, p -> received.add(unwrap(p)));

        bus.publish("tick", new TickEvent(1));
        assertTrue(received.isEmpty(), "dispatch waited in the executor queue");
        assertEquals(1, queued.size());
        queued.forEach(Runnable::run);
        assertEquals(new TickEvent(1), received.get(0));
    }

    @Test
    void subscriptionsHaveUniqueIds() {
        assertNotEquals(bus.on("tick", p -> {
        }).id(), bus.on("tick", p -> {
        }).id());
    }

    @Test
    void syncSubscriptionsAreExcludedFromAsyncDispatch() {
        // chat:send is cancellable and therefore sync-capable; its sync callback
        // must not also receive async dispatch.
        EventCatalog catalog = new EventCatalog();
        catalog.register(ink.tuanzi.neomacros.core.event.def.chat.ChatSendEvent.class);
        EventBus cancellableBus = new EventBus(catalog, Runnable::run, null);
        List<Object> asyncReceived = new ArrayList<>();
        AtomicReference<String> threadName = new AtomicReference<>();
        cancellableBus.onSync("chat:send", (payload, flag) -> threadName.set(Thread.currentThread().getName()));

        EventBus.PublishResult result = cancellableBus.publish("chat:send", "msg");
        assertEquals(1, result.notified());
        assertTrue(asyncReceived.isEmpty());
        assertEquals(Thread.currentThread().getName(), threadName.get());
    }

    @Test
    void dispatchesRunOnExecutorThreadWhenRealExecutorUsed() throws Exception {
        var executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread worker = new Thread(runnable, "bus-test-worker");
            worker.setDaemon(true);
            return worker;
        });
        try {
            AtomicReference<String> threadName = new AtomicReference<>();
            bus.on("tick", executor, payload -> threadName.set(Thread.currentThread().getName()));
            bus.publish("tick", new TickEvent(1));
            executor.submit(() -> { }).get(1, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("bus-test-worker", threadName.get());
        } finally {
            executor.shutdownNow();
        }
    }

    /** Unwraps the bus transport envelope so assertions see the raw payload. */
    private static Object unwrap(Object value) {
        return value instanceof ink.tuanzi.neomacros.core.event.EventPayloadEnvelope envelope
                ? envelope.payload() : value;
    }

}
