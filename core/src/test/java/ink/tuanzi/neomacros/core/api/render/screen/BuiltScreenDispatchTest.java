package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.player.PlayerApi;
import ink.tuanzi.neomacros.port.ScreenPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-003 §7 callback dispatch: frozen payloads through the session sink,
 * truthy=consumed on {@code gui.on} inputs, exception swallowing, the
 * stateChanged cache write, spec-callback replacement, render canvas
 * lifetime, and re-entrant control writes from inside a callback (the
 * dispatch-gate inline path).
 */
class BuiltScreenDispatchTest {

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

    @Test
    void specInputCallbackReceivesAFrozenPayloadButIsNotConsumed() {
        List<Object> received = new CopyOnWriteArrayList<>();
        BuiltScreen gui = api.buildScreen(Map.of(
                "onMouseDown", (Consumer<Object[]>) args -> received.add(args[0])));

        boolean consumed = port.sink().mouseDown(new ScreenPort.MouseInput(
                1, 12.5, 40.0, 0, 0, List.of("shift"), null));

        // The engine's Consumer channel drops the script return value, so a
        // spec-borne input callback reads as not consumed (the design note).
        assertFalse(consumed);
        assertEquals(1, received.size());
        Map<?, ?> payload = assertInstanceOf(Map.class, received.get(0));
        Object pos = payload.get("pos");
        assertNotNull(pos);
        assertEquals(1, payload.get("button"));
        assertEquals(List.of("shift"), payload.get("modifiers"));
        assertFalse(payload.containsKey("delta"));
    }

    @Test
    void onRegisteredInputCallbackAnswersTruthfulConsumption() {
        BuiltScreen gui = api.buildScreen(Map.of());
        gui.on("mouseDown", e -> true);

        assertTrue(port.sink().mouseDown(new ScreenPort.MouseInput(
                0, 1, 2, 0, 0, List.of(), null)));

        gui.on("mouseDown", e -> false);
        assertFalse(port.sink().mouseDown(new ScreenPort.MouseInput(
                0, 1, 2, 0, 0, List.of(), null)));

        // Replacing the spec callback: only the last registration runs.
        gui.on("mouseDown", e -> true);
        assertTrue(port.sink().mouseDown(new ScreenPort.MouseInput(
                0, 1, 2, 0, 0, List.of(), null)));
    }

    @Test
    void keyAndCharPayloadsCarryTheNeutralShape() {
        List<Object> keys = new CopyOnWriteArrayList<>();
        List<Object> chars = new CopyOnWriteArrayList<>();
        BuiltScreen gui = api.buildScreen(Map.of(
                "onKeyPress", (Consumer<Object[]>) args -> keys.add(args[0]),
                "onCharTyped", (Consumer<Object[]> ) args -> chars.add(args[0])));

        port.sink().keyPress(new ScreenPort.KeyInput("key.keyboard.r", 82,
                List.of("ctrl")));
        port.sink().charTyped(new ScreenPort.CharInput("r"));

        Map<?, ?> key = assertInstanceOf(Map.class, keys.get(0));
        assertEquals("key.keyboard.r", key.get("key"));
        assertEquals(82, key.get("code"));
        assertEquals(List.of("ctrl"), key.get("modifiers"));
        Map<?, ?> character = assertInstanceOf(Map.class, chars.get(0));
        assertEquals("r", character.get("char"));
    }

    @Test
    void callbackExceptionsSwallowAndReadAsNotConsumed() {
        BuiltScreen gui = api.buildScreen(Map.of(
                "onMouseDown", (Consumer<Object[]>) args -> {
                    throw new RuntimeException("script blew up");
                }));
        gui.on("keyPress", e -> {
            throw new IllegalStateException("script blew up");
        });

        assertFalse(port.sink().mouseDown(new ScreenPort.MouseInput(
                0, 0, 0, 0, 0, List.of(), null)));
        assertFalse(port.sink().keyPress(
                new ScreenPort.KeyInput("key.keyboard.a", 65, List.of())));
        assertFalse(gui.isClosed());
    }

    @Test
    void controlClickAndChangeDispatchIntoTheSpecCallbacks() {
        List<Object> clicks = new CopyOnWriteArrayList<>();
        List<Object> changes = new CopyOnWriteArrayList<>();
        BuiltScreen gui = api.buildScreen(Map.of("controls", List.of(
                Map.of("type", "button", "id", "buy",
                        "onClick", (Consumer<Object[]>) args -> clicks.add(args[0])),
                Map.of("type", "checkbox", "id", "dbg", "checked", true,
                        "onChange", (Consumer<Object[]>) args -> changes.add(args[0])))));

        port.sink().controlEvent("buy", "click",
                Map.of("id", "buy", "type", "button"));
        port.sink().controlEvent("dbg", "change",
                Map.of("id", "dbg", "type", "checkbox", "checked", false));

        assertEquals(1, clicks.size());
        assertEquals("buy", assertInstanceOf(Map.class, clicks.get(0)).get("id"));
        assertEquals(1, changes.size());
        assertEquals(false,
                assertInstanceOf(Map.class, changes.get(0)).get("checked"));
        assertFalse(gui.isClosed());
    }

    @Test
    void stateChangedWritesThroughToTheControlsCache() {
        BuiltScreen gui = api.buildScreen(Map.of("controls", List.of(
                Map.of("type", "editBox", "id", "name", "text", "seed"))));
        assertEquals("seed", gui.control("name").text());

        port.sink().stateChanged("name", new ScreenPort.ControlState(
                "name", "editBox", 0, 0, 0, 0, 0, "", true, true,
                null, null, "typed", null, null, null, null, null));

        assertEquals("typed", gui.control("name").text());
        assertEquals("typed", gui.getControls().get(0).text());
    }

    @Test
    void reentrantSetControlInsideACallbackRunsInline() {
        List<Object> clicks = new CopyOnWriteArrayList<>();
        BuiltScreen gui = api.buildScreen(Map.of("controls", List.of(
                Map.of("type", "checkbox", "id", "dbg"))));
        gui.on("mouseDown", e -> {
            // The dispatch gate lets the mutation run inline instead of
            // bridging to the main thread this callback is parking.
            gui.setControl("dbg", Map.of("checked", true));
            clicks.add("ran");
            return true;
        });

        assertTrue(port.sink().mouseDown(new ScreenPort.MouseInput(
                0, 0, 0, 0, 0, List.of(), null)));
        assertEquals(List.of("ran"), clicks);
        assertEquals(true, gui.control("dbg").checked());
        assertTrue(port.actions.contains("write:1:dbg"));
    }

    @Test
    void renderCallbackDrawsThroughTheCanvasWhichDiesWithTheCallback() {
        List<Object> renders = new CopyOnWriteArrayList<>();
        BuiltScreen gui = api.buildScreen(Map.of(
                "onRender", (Consumer<Object[]>) args -> {
                    renders.add(args[0]);
                    ScreenCanvas canvas =
                            (ScreenCanvas) ((Map<?, ?>) args[0]).get("ctx");
                    canvas.rect(5, 6, 7, 8, 0xFF00FF00);
                }));

        port.sink().render(port.recordingCanvas(), 3.0, 4.0, 0.5f);

        assertEquals(1, renders.size());
        Map<?, ?> payload = assertInstanceOf(Map.class, renders.get(0));
        ScreenCanvas canvas = assertInstanceOf(ScreenCanvas.class, payload.get("ctx"));
        assertEquals(Map.of("x", 3.0, "y", 4.0), payload.get("mouse"));
        assertEquals(List.of("rect:7x8"), port.drawn);

        // The facade revokes the canvas when the callback returns; drawing
        // from a captured reference must fail loudly.
        assertThrows(IllegalStateException.class, () -> canvas.rect(0, 0, 1, 1, 0));
        assertFalse(gui.isClosed());
    }

    private static void assertThrows(Class<? extends Throwable> type, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return;
            }
            throw new AssertionError("expected " + type + " but got " + t, t);
        }
        throw new AssertionError("expected " + type + " but nothing was thrown");
    }
}
