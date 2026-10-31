package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.core.event.def.chat.ChatReceiveEvent;
import ink.tuanzi.neomacros.core.event.def.chat.ChatSendEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameStartEvent;
import ink.tuanzi.neomacros.core.event.def.tick.TickEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies descriptor derivation from annotated payload records, duplicate
 * handling, custom event validation, and package-scan registration over the
 * built-in event definitions.
 */
class EventCatalogTest {

    @Test
    void descriptorDerivedFromAnnotations() {
        EventCatalog catalog = new EventCatalog();
        catalog.register(ChatSendEvent.class);

        EventDescriptor descriptor = catalog.descriptor("chat:send").orElseThrow();
        assertEquals("chat:send", descriptor.name());
        assertEquals("chat", descriptor.domain());
        assertTrue(descriptor.cancellable());
        assertEquals(ChatSendEvent.class, descriptor.payloadType());
        assertEquals("0.1.0", descriptor.since());
        assertFalse(descriptor.custom());
    }

    @Test
    void nonCancellableDescriptorDerivesFalse() {
        EventCatalog catalog = new EventCatalog();
        catalog.register(ChatReceiveEvent.class);
        catalog.register(ChatSendEvent.class);
        assertFalse(catalog.isCancellable("chat:receive"));
        assertTrue(catalog.isCancellable("chat:send"));
    }

    @Test
    void plainClassRejected() {
        EventCatalog catalog = new EventCatalog();
        assertThrows(IllegalArgumentException.class, () -> catalog.register(String.class));
    }

    @Test
    void duplicateNameWithDifferentPayloadRejected() {
        EventCatalog catalog = new EventCatalog();
        catalog.register(ChatReceiveEvent.class);
        assertThrows(IllegalStateException.class, () -> catalog.register(ChatReceiveShim.class));
    }

    /** Test-local record sharing the built-in event name with a different payload type. */
    @ink.tuanzi.neomacros.doc.api.ApiEvent(name = "chat:receive")
    private record ChatReceiveShim(String other) {
    }

    @Test
    void duplicateRegistrationIsIdempotent() {
        EventCatalog catalog = new EventCatalog();
        catalog.register(ChatReceiveEvent.class);
        catalog.register(ChatReceiveEvent.class);
        assertEquals(1, catalog.all().size());
    }

    @Test
    void domainDerivation() {
        assertEquals("player", EventDescriptor.domainOf("player:join"));
        assertEquals("tick", EventDescriptor.domainOf("tick"));
    }

    @Test
    void scanPackageRegistersBuiltInDefinitions() {
        EventCatalog catalog = new EventCatalog();
        int registered = catalog.scanPackage("ink.tuanzi.neomacros.core.event.def");

        // The def tree uses subpackages per domain; scan those explicitly.
        registered += catalog.scanPackage("ink.tuanzi.neomacros.core.event.def.chat");
        registered += catalog.scanPackage("ink.tuanzi.neomacros.core.event.def.lifecycle");
        registered += catalog.scanPackage("ink.tuanzi.neomacros.core.event.def.tick");

        assertEquals(7, catalog.all().size());
        assertTrue(catalog.descriptor("tick").isPresent());
        assertTrue(catalog.descriptor("chat:receive").isPresent());
        assertTrue(catalog.descriptor("chat:send").isPresent());
        assertTrue(catalog.descriptor("game:start").isPresent());
        assertTrue(catalog.descriptor("game:quit").isPresent());
        assertTrue(catalog.descriptor("world:join").isPresent());
        assertTrue(catalog.descriptor("world:leave").isPresent());
    }

    @Test
    void scanPackageIsIdempotent() {
        EventCatalog catalog = new EventCatalog();
        catalog.scanPackage("ink.tuanzi.neomacros.core.event.def.chat");
        int second = catalog.scanPackage("ink.tuanzi.neomacros.core.event.def.chat");
        assertEquals(0, second);
        assertEquals(2, catalog.all().size());
    }

    @Test
    void customEventRegistrationAndValidation() {
        EventCatalog catalog = new EventCatalog();
        catalog.registerAll(List.of(TickEvent.class, GameStartEvent.class));

        catalog.registerCustom("myMacros:priceUpdate");
        EventDescriptor custom = catalog.descriptor("myMacros:priceUpdate").orElseThrow();
        assertTrue(custom.custom());
        assertFalse(custom.cancellable());
        assertEquals("myMacros", custom.domain());

        assertThrows(IllegalArgumentException.class, () -> catalog.registerCustom("noNamespace"));
        // Taking over a built-in domain is rejected even with a fresh suffix.
        assertThrows(IllegalArgumentException.class, () -> catalog.registerCustom("game:custom"));
        assertThrows(IllegalStateException.class, () -> catalog.registerCustom("myMacros:priceUpdate"));
    }

    @Test
    void allDescriptorsSortedByName() {
        EventCatalog catalog = new EventCatalog();
        catalog.registerAll(List.of(ChatSendEvent.class, ChatReceiveEvent.class));
        List<String> names = catalog.all().stream().map(EventDescriptor::name).toList();
        assertEquals(List.of("chat:receive", "chat:send"), names);
    }
}
