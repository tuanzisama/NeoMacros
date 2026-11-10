package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.core.event.def.chat.ChatSendEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Watchdog semantics: an overdue synchronous callback gets its subscription
 * disabled and exactly one violation reported, while fast callbacks run
 * undisturbed. Uses a compressed budget so the test stays fast.
 */
class SyncWatchdogTest {

    private static final long BUDGET_MILLIS = 80;

    @Test
    void invalidBudgetRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SyncWatchdog(0, violation -> {
                }));
        assertThrows(IllegalArgumentException.class,
                () -> new SyncWatchdog(-5, violation -> {
                }));
    }

    @Test
    void fastCallbackRunsUndisturbed() {
        List<SyncWatchdog.Violation> violations = new CopyOnWriteArrayList<>();
        try (SyncWatchdog watchdog = new SyncWatchdog(BUDGET_MILLIS, violations::add)) {
            EventCatalog catalog = new EventCatalog();
            catalog.register(ChatSendEvent.class);
            EventBus bus = new EventBus(catalog, Runnable::run, watchdog);
            Subscription sub = bus.onSync("chat:send", (payload, flag) -> null);

            bus.publish("chat:send", new ChatSendEvent("hi"));
            assertTrue(sub.isActive());
            assertTrue(violations.isEmpty());
        }
    }

    @Test
    void overdueCallbackIsDisabledAndReportedOnce() throws Exception {
        List<SyncWatchdog.Violation> violations = new CopyOnWriteArrayList<>();
        try (SyncWatchdog watchdog = new SyncWatchdog(BUDGET_MILLIS, violations::add)) {
            EventCatalog catalog = new EventCatalog();
            catalog.register(ChatSendEvent.class);
            EventBus bus = new EventBus(catalog, Runnable::run, watchdog);
            Subscription sub = bus.onSync("chat:send", (payload, flag) -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });

            bus.publish("chat:send", new ChatSendEvent("hi"));

            // The monitor polls at budget/4; wait until the violation is reported.
            long deadline = System.currentTimeMillis() + 2000;
            while (violations.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(1, violations.size());
            assertEquals("chat:send", violations.get(0).eventName());
            assertFalse(sub.isActive());

            // The stuck thread eventually returns; a late return must not re-report,
            // and further publishes skip the disabled subscription entirely.
            Thread.sleep(BUDGET_MILLIS);
            assertEquals(1, violations.size());
            assertTrue(bus.publish("chat:send", new ChatSendEvent("again")).notified() == 0);
            assertEquals(1, violations.size());
        }
    }

    @Test
    void watchdogDisablesOnlyTheOffendingSubscription() throws Exception {
        List<SyncWatchdog.Violation> violations = new CopyOnWriteArrayList<>();
        try (SyncWatchdog watchdog = new SyncWatchdog(BUDGET_MILLIS, violations::add)) {
            EventCatalog catalog = new EventCatalog();
            catalog.register(ChatSendEvent.class);
            EventBus bus = new EventBus(catalog, Runnable::run, watchdog);
            Subscription slow = bus.onSync("chat:send", (payload, flag) -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
            Subscription fast = bus.onSync("chat:send", (payload, flag) -> null);

            bus.publish("chat:send", new ChatSendEvent("hi"));
            long deadline = System.currentTimeMillis() + 2000;
            while (violations.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertFalse(slow.isActive());
            assertTrue(fast.isActive(), "unrelated subscription stays enabled");
        }
    }
}
