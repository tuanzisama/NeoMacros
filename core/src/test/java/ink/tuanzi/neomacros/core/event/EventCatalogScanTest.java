package ink.tuanzi.neomacros.core.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The P0 event set registers with full descriptors: chat:send is the
 * cancellable onSync surface. Production bootstrap declares this list
 * explicitly (the classpath scanner cannot see mod classes under Knot).
 */
class EventCatalogScanTest {

    @Test
    void p0EventsRegisterWithDescriptors() {
        var catalog = new EventCatalog();
        catalog.registerAll(java.util.List.of(
                ink.tuanzi.neomacros.core.event.def.tick.TickEvent.class,
                ink.tuanzi.neomacros.core.event.def.chat.ChatSendEvent.class,
                ink.tuanzi.neomacros.core.event.def.chat.ChatReceiveEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.GameStartEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.GameQuitEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.WorldJoinEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.WorldLeaveEvent.class));
        assertEquals(7, catalog.all().size());
        assertTrue(catalog.isCancellable("chat:send"),
                "chat:send must be cancellable; descriptors=" + catalog.all());
        assertFalse(catalog.isCancellable("tick"), "tick is not cancellable");
    }
}
