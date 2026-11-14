package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.player.PlayerApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-003 §6 lifecycle: open/close, the exactly-once close callback with
 * reads-live-mutators-throw ordering inside it, the replacement path
 * (sink.closed without a port close), unknown callback names, and the
 * closed-handle invalidation rules.
 */
class BuiltScreenLifecycleTest {

    private FakeScreenPort port;
    private PlayerApi api;

    @BeforeEach
    void install() {
        port = new FakeScreenPort();
        ScriptApiEnvironment.install(null, new FakeScreenPort.ScreenOnlyAdapter(port));
        api = new PlayerApi();
    }

    @AfterEach
    void uninstall() throws Exception {
        ScriptApiEnvironment.install(null, null);
    }

    private BuiltScreen open(Map<String, Object> extra) {
        Map<String, Object> spec = new java.util.LinkedHashMap<>(extra);
        spec.putIfAbsent("title", "T");
        return api.buildScreen(spec);
    }

    @Test
    void closeIntentInvalidatesTheHandleExactlyOnce() {
        AtomicInteger closes = new AtomicInteger();
        BuiltScreen gui = open(Map.of("onClose", (java.util.function.Consumer<Object[]>)
                args -> closes.incrementAndGet()));

        gui.close();

        assertTrue(gui.isClosed());
        assertEquals(1, closes.get());
        assertEquals(List.of("open", "close:1"), port.actions);
        assertTrue(gui.getControls().isEmpty());
        assertThrows(IllegalStateException.class, gui::close);
        assertThrows(IllegalStateException.class,
                () -> gui.add(Map.of("type", "button", "id", "x")));
        assertThrows(IllegalStateException.class, () -> gui.remove("x"));
        assertThrows(IllegalStateException.class,
                () -> gui.setControl("x", Map.of("checked", true)));
        assertThrows(IllegalStateException.class, gui::reload);
        assertThrows(IllegalStateException.class,
                () -> gui.on("mouseDown", e -> true));
        // Plain reads keep answering after close.
        assertEquals("T", gui.getTitle());
        assertEquals("ScriptScreen", gui.getName());
        assertEquals("current", gui.getReturnTo());

        // A second close notification is a no-op (idempotent gate).
        port.sink().closed();
        assertEquals(1, closes.get());
    }

    @Test
    void duringCloseCallbackReadsWorkAndMutatorsThrow() {
        AtomicReference<BuiltScreen> holder = new AtomicReference<>();
        AtomicReference<String> titleDuring = new AtomicReference<>();
        AtomicReference<Boolean> controlsDuring = new AtomicReference<>();
        AtomicReference<Boolean> addThrew = new AtomicReference<>();
        BuiltScreen gui = open(Map.of(
                "controls", List.of(Map.of("type", "button", "id", "buy")),
                "onClose", (java.util.function.Consumer<Object[]>) args -> {
                    BuiltScreen self = holder.get();
                    titleDuring.set(self.getTitle());
                    controlsDuring.set(!self.getControls().isEmpty());
                    try {
                        self.add(Map.of("type", "button", "id", "late"));
                    } catch (RuntimeException e) {
                        addThrew.set(true);
                    }
                }));
        holder.set(gui);

        gui.close();

        assertEquals("T", titleDuring.get());
        assertTrue(controlsDuring.get(), "reads stay live inside the close callback");
        assertTrue(addThrew.get(), "mutators throw while closing (re-entry guard)");
        assertTrue(gui.isClosed());
    }

    @Test
    void replacementPathClosesWithoutAPortClose() {
        AtomicInteger closes = new AtomicInteger();
        BuiltScreen gui = open(Map.of("onClose", (java.util.function.Consumer<Object[]>)
                args -> closes.incrementAndGet()));

        // The adapter reports the terminal state (screen replaced / unload).
        port.sink().closed();

        assertTrue(gui.isClosed());
        assertEquals(1, closes.get());
        // Replacement does not drive the close-intent port path.
        assertFalse(port.actions.stream().anyMatch(a -> a.startsWith("close:")));
        // A repeat terminal notification stays a no-op.
        port.sink().closed();
        assertEquals(1, closes.get());
    }

    @Test
    void unknownCallbackNameThrows() {
        BuiltScreen gui = open(Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> gui.on("hover", e -> true));
        assertFalse(gui.isClosed());
    }

    @Test
    void addRemoveAndReloadRoundTripThroughThePort() {
        BuiltScreen gui = open(Map.of());

        gui.add(Map.of("type", "button", "id", "more", "x", 1, "y", 2,
                "width", 3, "height", 4, "label", "More"));
        assertEquals(1, gui.getControls().size());
        assertEquals("more", gui.control("more").id());

        gui.setControl("more", Map.of("label", "Renamed"));
        assertEquals("Renamed", gui.control("more").label());

        gui.reload();
        gui.remove("more");
        assertTrue(gui.getControls().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> gui.remove("more"));
        assertThrows(IllegalArgumentException.class,
                () -> gui.setControl("ghost", Map.of("label", "x")));

        assertEquals(List.of("open", "add:1:more", "write:1:more", "reload:1",
                "remove:1:more"), port.actions);
    }

    @Test
    void setControlRejectsUnknownPatchFields() {
        BuiltScreen gui = open(Map.of("controls", List.of(
                Map.of("type", "checkbox", "id", "dbg"))));
        assertThrows(IllegalArgumentException.class,
                () -> gui.setControl("dbg", Map.of("x", 5)));
        assertThrows(IllegalArgumentException.class,
                () -> gui.setControl("dbg", Map.of()));
    }

    @Test
    void modifierReadsForwardThroughThePort() {
        BuiltScreen gui = open(Map.of());
        assertFalse(gui.getShiftDown());
        port.shift = true;
        port.control = true;
        port.alt = true;
        assertTrue(gui.getShiftDown());
        assertTrue(gui.getControlDown());
        assertTrue(gui.getAltDown());
    }
}
