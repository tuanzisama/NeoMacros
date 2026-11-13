package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.core.event.def.tick.TickEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wildcard pattern matching ({@code domain:*}, {@code *}) and the
 * high-traffic ignore list behavior.
 */
class EventBusWildcardTest {

    private SyncWatchdog watchdog;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        watchdog = new SyncWatchdog(violation -> {
        });
        bus = new EventBus(new EventCatalog(), Runnable::run, watchdog);
    }

    @Test
    void patternMatchingSemantics() {
        assertTrue(EventBus.matches("*", "anything:at:all"));
        assertTrue(EventBus.matches("player:*", "player:join"));
        assertTrue(EventBus.matches("player:*", "player:leave"));
        assertFalse(EventBus.matches("player:*", "world:join"));
        assertFalse(EventBus.matches("player:*", "player"));
        assertTrue(EventBus.matches("tick", "tick"));
        assertFalse(EventBus.matches("tick", "tick:sub"));
    }

    @Test
    void domainWildcardReceivesAllDomainEvents() {
        List<Object> received = new ArrayList<>();
        bus.on("player:*", p -> received.add(unwrap(p)));

        bus.publish("player:join", "a");
        bus.publish("player:leave", "b");
        bus.publish("world:join", "c");

        assertEquals(List.of("a", "b"), received);
    }

    @Test
    void globalWildcardReceivesNonIgnoredEvents() {
        List<Object> received = new ArrayList<>();
        bus.on("*", p -> received.add(unwrap(p)));

        bus.publish("player:join", "a");
        bus.publish("tick", new TickEvent(1)); // ignored by default

        assertEquals(List.of("a"), received);
    }

    @Test
    void exactSubscriptionsBypassIgnoreList() {
        List<Object> received = new ArrayList<>();
        bus.on("tick", p -> received.add(unwrap(p)));

        bus.publish("tick", new TickEvent(1));
        assertEquals(1, received.size());
    }

    @Test
    void ignoreListIsConfigurable() {
        bus.setWildcardIgnores(Set.of());
        List<Object> received = new ArrayList<>();
        bus.on("*", p -> received.add(unwrap(p)));

        bus.publish("tick", new TickEvent(1));
        assertEquals(1, received.size());
    }

    @Test
    void defaultIgnoreListCoversHighTrafficEvents() {
        Set<String> ignores = bus.getWildcardIgnores();
        assertTrue(ignores.contains("tick"));
        assertTrue(ignores.contains("packet:*"));
        assertTrue(ignores.contains("sound:*"));
        assertTrue(ignores.contains("world:blockUpdate"));
        assertTrue(ignores.contains("entity:metadata"));
        assertTrue(ignores.contains("entity:customName"));
    }

    @Test
    void mixedExactAndWildcardDispatchCounting() {
        List<Object> received = new ArrayList<>();
        bus.on("player:join", p -> received.add(unwrap(p)));
        bus.on("player:*", p -> received.add(unwrap(p)));
        bus.on("*", p -> received.add(unwrap(p)));

        EventBus.PublishResult result = bus.publish("player:join", "x");
        assertEquals(3, result.notified());
        assertEquals(3, received.size());
    }

    /** Unwraps the bus transport envelope so assertions see the raw payload. */
    private static Object unwrap(Object value) {
        return value instanceof ink.tuanzi.neomacros.core.event.EventPayloadEnvelope envelope
                ? envelope.payload() : value;
    }

}
