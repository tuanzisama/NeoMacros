package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.player.PlayerApi;
import org.junit.jupiter.api.AfterEach;
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
 * G-003 §3.1 spec parsing: defaults, validation (unknown type, missing id,
 * duplicate id, field/type mismatch, bad returnTo), callback unwrapping
 * into the session, and the callback-free neutral map that crosses the
 * port.
 */
class BuildScreenSpecTest {

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
    void defaultsFollowTheDesignDecisions() {
        api.buildScreen(Map.of("title", "Shop"));

        Map<String, Object> neutral = port.neutralSpecs.get(0);
        assertEquals("Shop", neutral.get("title"));
        assertEquals(true, neutral.get("showTitle"));
        assertEquals(false, neutral.get("pause"));
        assertEquals(true, neutral.get("closeOnEsc"));
        assertEquals("current", neutral.get("returnTo"));
        assertTrue(((List<?>) neutral.get("controls")).isEmpty());
    }

    @Test
    void controlSpecsCrossThePortWithoutCallbacks() {
        api.buildScreen(Map.of(
                "title", "T",
                "returnTo", "game",
                "controls", List.of(
                        Map.of("type", "button", "id", "buy", "x", 10, "y", 40,
                                "width", 98, "height", 20, "label", "Buy"),
                        Map.of("type", "checkbox", "id", "dbg", "x", 10, "y", 70,
                                "width", 98, "height", 20, "label", "Debug",
                                "checked", true))));

        Map<String, Object> neutral = port.neutralSpecs.get(0);
        assertEquals("game", neutral.get("returnTo"));
        List<?> controls = (List<?>) neutral.get("controls");
        assertEquals(2, controls.size());
        Map<String, Object> first = cast(controls.get(0));
        assertEquals("button", first.get("type"));
        assertEquals("buy", first.get("id"));
        assertEquals(98, first.get("width"));
        // Callbacks never enter the port contract.
        assertFalse(first.containsKey("onClick"));
        Map<String, Object> second = cast(controls.get(1));
        assertEquals(true, second.get("checked"));
        assertFalse(second.containsKey("onChange"));
        assertFalse(neutral.containsKey("onClose"));
        assertFalse(neutral.containsKey("onRender"));
    }

    @Test
    void invalidSpecsThrowCatchableErrors() {
        assertThrows(IllegalArgumentException.class,
                () -> api.buildScreen(Map.of("controls",
                        List.of(Map.of("type", "nope", "id", "x")))));
        assertThrows(IllegalArgumentException.class,
                () -> api.buildScreen(Map.of("controls",
                        List.of(Map.of("type", "button")))));
        assertThrows(IllegalArgumentException.class,
                () -> api.buildScreen(Map.of("controls", List.of(
                        Map.of("type", "button", "id", "dup"),
                        Map.of("type", "checkbox", "id", "dup")))));
        assertThrows(IllegalArgumentException.class,
                () -> api.buildScreen(Map.of("controls",
                        List.of(Map.of("type", "button", "id", "b", "values",
                                List.of("A"))))));
        assertThrows(IllegalArgumentException.class,
                () -> api.buildScreen(Map.of("returnTo", "nether")));
        // The failed open never reached the port (validation precedes open).
        assertTrue(port.actions.isEmpty());
    }

    @Test
    void openFailureBecomesACatchableThrow() {
        port.failOpen = true;
        assertThrows(IllegalStateException.class,
                () -> api.buildScreen(Map.of("title", "X")));
    }

    @Test
    void seededSessionReadsTitleNameAndControlSnapshots() {
        BuiltScreen gui = api.buildScreen(Map.of(
                "title", "Shop",
                "controls", List.of(
                        Map.of("type", "slider", "id", "amt", "x", 1, "y", 2,
                                "width", 3, "height", 4, "value", 0.5, "steps", 10))));

        assertEquals("Shop", gui.getTitle());
        assertEquals("ScriptScreen", gui.getName());
        assertFalse(gui.isClosed());
        assertEquals("current", gui.getReturnTo());
        List<ControlState> controls = gui.getControls();
        assertEquals(1, controls.size());
        ControlState state = controls.get(0);
        assertEquals("amt", state.id());
        assertEquals("slider", state.type());
        assertEquals(0.5, state.value());
        assertEquals("amt", gui.control("amt").id());
        assertNull(gui.control("missing"));
        assertTrue(port.isDisplayed(port.activeSession()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
