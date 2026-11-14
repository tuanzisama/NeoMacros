package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.player.PlayerApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-036B: the shared widget face on {@link Widget} (geometry, label,
 * active, click, tooltips, parent extent), the {@code cycleButton} and
 * {@code lockButton} handles with their state/callback round-trips, the
 * {@code CyclicButtonBuilder}/{@code LockButtonBuilder} spec assembly
 * (option lists, build-time value labels, the alternate-list toggle
 * channel), and the completed {@link AbstractWidgetBuilder} base face
 * (alpha / moveTo / layout reads).
 */
class ScreenSecondaryControlsTest {

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

    private BuiltScreen openSecondary() {
        return api.buildScreen(Map.of(
                "title", "T",
                "controls", List.of(
                        Map.of("type", "button", "id", "buy", "x", 10, "y", 40,
                                "width", 98, "height", 20, "label", "Buy"),
                        Map.of("type", "cycleButton", "id", "mode", "x", 10,
                                "y", 70, "width", 98, "height", 20,
                                "values", List.of("a", "b", "c"),
                                "initial", "b", "prefix", "Mode: "),
                        Map.of("type", "lockButton", "id", "gate", "x", 10,
                                "y", 100, "width", 16, "height", 16))));
    }

    private Map<String, Object> lastPatch() {
        return port.patches.get(port.patches.size() - 1);
    }

    // ------------------------------------------------------------------
    // Widget shared face (ClickableWidgetHelper account)
    // ------------------------------------------------------------------

    @Test
    void widgetSharedFaceDrivesLayoutMessageAndActive() {
        BuiltScreen gui = openSecondary();
        Button button = gui.button("buy");

        assertEquals(10, button.getX());
        assertEquals(40, button.getY());
        assertEquals(98, button.getWidth());
        assertEquals(20, button.getHeight());
        assertEquals("Buy", button.getMessage());
        assertTrue(button.getActive());
        assertEquals(10, button.getScaledLeft());
        assertEquals(40, button.getScaledTop());
        assertEquals(98, button.getScaledWidth());
        assertEquals(20, button.getScaledHeight());
        assertEquals(640, button.getParentWidth(), "canned window scaled width");
        assertEquals(360, button.getParentHeight(), "canned window scaled height");

        button.setPos(5, 6);
        assertEquals(5, button.getX());
        assertEquals(6, button.getY());
        assertEquals(5, lastPatch().get("x"));
        assertEquals(6, lastPatch().get("y"));

        button.setWidth(50);
        assertEquals(50, button.getWidth());
        assertEquals(50, lastPatch().get("width"));

        button.setMessage("Buy now");
        assertEquals("Buy now", button.getMessage());
        assertEquals("Buy now", lastPatch().get("label"));

        button.setActive(false);
        assertFalse(button.getActive());
        assertEquals(false, lastPatch().get("active"));

        button.moveTo(7, 8);
        assertEquals(7, button.getX());
        assertEquals(8, button.getY());

        // A closed session degrades reads to defaults and refuses writes.
        gui.close();
        assertEquals(0, button.getX());
        assertEquals(0, button.getParentWidth());
        assertEquals("", button.getMessage());
        assertThrows(IllegalStateException.class, () -> button.setPos(1, 1));
        assertThrows(IllegalStateException.class, () -> button.click());
    }

    @Test
    void widgetClickAndTooltipWriteTransientAndSessionChannels() {
        BuiltScreen gui = openSecondary();
        Button button = gui.button("buy");

        button.click();
        Map<String, Object> last = lastPatch();
        assertEquals(Boolean.TRUE, last.get("click"), "click is a transient op");
        assertTrue(port.actions.contains("write:1:buy"));

        button.setTooltip(List.of("one", "two"));
        assertEquals(List.of("one", "two"), button.getTooltips());
        assertEquals(List.of("one", "two"), lastPatch().get("tooltip"));

        button.addTooltip("three");
        assertEquals(List.of("one", "two", "three"), button.getTooltips());

        assertTrue(button.removeTooltip(1));
        assertEquals(List.of("one", "three"), button.getTooltips());
        assertFalse(button.removeTooltip(99), "an invalid index answers false");
        assertTrue(button.removeTooltip("three"));
        assertFalse(button.removeTooltip("missing"), "no match answers false");
        assertEquals(List.of("one"), button.getTooltips());

        // A single value normalizes to a one-line list.
        button.setTooltip("solo");
        assertEquals(List.of("solo"), button.getTooltips());

        // Handles opened on the same screen share the session's list.
        assertEquals(List.of("solo"), gui.button("buy").getTooltips());

        gui.close();
        assertEquals(List.of(), button.getTooltips(), "closed reads degrade");
        assertThrows(IllegalStateException.class, () -> button.setTooltip("x"));
    }

    // ------------------------------------------------------------------
    // CycleButton handle
    // ------------------------------------------------------------------

    @Test
    void cycleButtonHandleValueAndCycleRoundTrip() {
        BuiltScreen gui = openSecondary();
        CycleButton mode = gui.cycleButton("mode");

        assertEquals("b", mode.getValue(), "seeded from the initial option");
        assertEquals("b", mode.getStringValue(), "no converter answers raw");

        assertFalse(mode.setValue("c"), "answers true only when unchanged");
        assertEquals("c", mode.getValue());
        assertTrue(mode.setValue("c"));
        assertTrue(lastPatch().containsKey("text"), "the write rides text");

        mode.cycle(1);
        assertEquals(1, lastPatch().get("cycle"), "cycle is a transient op");

        // The change callback fires from the adapter's control event.
        List<Object> changes = new CopyOnWriteArrayList<>();
        api.buildScreen(Map.of("controls", List.of(
                Map.of("type", "cycleButton", "id", "c1",
                        "values", List.of("x", "y"),
                        "onChange", (java.util.function.Consumer<Object[]>)
                                args -> changes.add(args[0])))));
        port.sink().controlEvent("c1", "change",
                Map.of("id", "c1", "type", "cycleButton", "value", "y"));
        assertEquals(1, changes.size());
        assertEquals("y", ((Map<?, ?>) changes.get(0)).get("value"));
    }

    @Test
    void cycleButtonGetStringValueReadsTheBuildTimeLabels() {
        BuiltScreen gui = openSecondary();
        gui.add(new CyclicButtonBuilder()
                .id("mode2").pos(1, 2).size(98, 20)
                .values(List.of("x", "y"), null)
                .initially("y")
                .valueToText(value -> value.toUpperCase())
                .build());

        CycleButton mode2 = gui.cycleButton("mode2");
        assertEquals("y", mode2.getValue());
        assertEquals("Y", mode2.getStringValue(), "converter output, no prefix");
        mode2.setValue("x");
        assertEquals("X", mode2.getStringValue());
    }

    // ------------------------------------------------------------------
    // LockButton handle
    // ------------------------------------------------------------------

    @Test
    void lockButtonHandleLockedRoundTrip() {
        BuiltScreen gui = openSecondary();
        LockButton gate = gui.lockButton("gate");

        assertFalse(gate.isLocked());
        gate.setLocked(true);
        assertTrue(gate.isLocked());
        assertEquals(true, lastPatch().get("locked"));
        assertEquals(true, gui.control("gate").locked());

        // Click and change callbacks reach the session.
        List<Object> clicks = new CopyOnWriteArrayList<>();
        List<Object> changes = new CopyOnWriteArrayList<>();
        api.buildScreen(Map.of("controls", List.of(
                Map.of("type", "lockButton", "id", "l1", "locked", false,
                        "onClick", (java.util.function.Consumer<Object[]>)
                                args -> clicks.add(args[0])),
                Map.of("type", "lockButton", "id", "l2", "locked", false,
                        "onChange", (java.util.function.Consumer<Object[]>)
                                args -> changes.add(args[0])))));
        port.sink().controlEvent("l1", "click",
                Map.of("id", "l1", "type", "lockButton"));
        port.sink().controlEvent("l2", "change",
                Map.of("id", "l2", "type", "lockButton", "locked", true));
        assertEquals(1, clicks.size());
        assertEquals(1, changes.size());
        assertEquals(true, ((Map<?, ?>) changes.get(0)).get("locked"));
    }

    // ------------------------------------------------------------------
    // Typed accessors
    // ------------------------------------------------------------------

    @Test
    void typedCycleAndLockAccessorsFailSoftOnUnknownOrClosed() {
        BuiltScreen gui = openSecondary();

        assertNotNull(gui.cycleButton("mode"));
        assertNotNull(gui.lockButton("gate"));
        assertNull(gui.cycleButton("missing"));
        assertThrows(IllegalArgumentException.class,
                () -> gui.cycleButton("gate"), "type mismatch throws");
        assertThrows(IllegalArgumentException.class,
                () -> gui.lockButton("mode"));

        gui.close();
        assertNull(gui.cycleButton("mode"), "closed session answers null");
        assertNull(gui.lockButton("gate"));
        LockButton dead = new LockButton(gui, "gate");
        assertThrows(IllegalStateException.class, () -> dead.setLocked(true));
        assertFalse(dead.isLocked(), "reads degrade to defaults after close");
    }

    // ------------------------------------------------------------------
    // CyclicButtonBuilder
    // ------------------------------------------------------------------

    @Test
    void cyclicButtonBuilderAssemblesValuesLabelsAndToggle() {
        CyclicButtonBuilder builder = new CyclicButtonBuilder()
                .id("mode2").pos(1, 2).size(98, 20)
                .values(List.of("x", "y"), null)
                .alternatives(List.of("p", "q"))
                .initially("y")
                .option("Mode: ")
                .omitTextOption(true)
                .valueToText(value -> value.toUpperCase())
                .alternateToggle(() -> true)
                .alpha(0.5)
                .onChange(args -> { });

        ControlSpec built = builder.build();
        assertEquals("cycleButton", built.type());
        assertEquals("mode2", built.id());
        assertEquals(List.of("x", "y"), List.of(built.values()));
        assertEquals(List.of("p", "q"), List.of(built.alternatives()));
        assertEquals("y", built.initial());
        assertEquals("Mode: ", built.prefix());
        assertEquals(true, built.omitTextOption());
        assertEquals(0.5, built.alpha(), 1e-9);
        assertNotNull(built.alternateToggle(), "the supplier survives the build");
        assertNotNull(built.onChange());
        // valueToText ran at build(): every option carries its label.
        assertEquals("X", built.valueLabels().get("x"));
        assertEquals("Q", built.valueLabels().get("q"));
        // The neutral form carries a marker flag, never the function.
        Map<String, Object> neutral = built.toNeutral();
        assertEquals(Boolean.TRUE, neutral.get("alternateToggle"));
        assertEquals("X", ((Map<?, ?>) neutral.get("valueLabels")).get("x"));
        assertEquals(0.5, ((Number) neutral.get("alpha")).doubleValue(), 1e-9);

        // values(defaults) keeps the alternate list; both-lists form replaces.
        ControlSpec kept = new CyclicButtonBuilder()
                .values(List.of("z"), null).alternatives(List.of("p"))
                .values(List.of("w"), null).build();
        assertEquals(List.of("w"), List.of(kept.values()));
        assertEquals(List.of("p"), List.of(kept.alternatives()));

        // Options are required; a dropped converter argument fails loudly.
        assertThrows(IllegalStateException.class,
                () -> new CyclicButtonBuilder().id("empty").build());
        assertThrows(IllegalArgumentException.class,
                () -> new CyclicButtonBuilder().construct(new Object[]{null}));
    }

    @Test
    void alternateToggleReachesTheSessionSinkAndCleansUp() {
        BuiltScreen gui = openSecondary();
        assertFalse(port.sink().alternateToggle("mode"),
                "a literal control without a toggle answers the default list");

        gui.add(new CyclicButtonBuilder()
                .id("mode2").pos(1, 2).size(98, 20)
                .values(List.of("x", "y"), null)
                .alternatives(List.of("p", "q"))
                .alternateToggle(() -> true)
                .build());
        assertTrue(port.sink().alternateToggle("mode2"),
                "the registered supplier answers through the sink");

        gui.remove("mode2");
        assertFalse(port.sink().alternateToggle("mode2"),
                "a removed control drops its toggle");
    }

    // ------------------------------------------------------------------
    // LockButtonBuilder
    // ------------------------------------------------------------------

    @Test
    void lockButtonBuilderSeedsSpecAndCallback() {
        List<Object> clicks = new CopyOnWriteArrayList<>();
        ControlSpec spec = new LockButtonBuilder()
                .id("gate2").pos(0, 0).size(16, 16)
                .locked(true)
                .onClick(args -> clicks.add(args[0]))
                .build();
        assertEquals("lockButton", spec.type());
        assertEquals(true, spec.locked());
        assertNotNull(spec.onClick());

        BuiltScreen gui = openSecondary();
        gui.add(spec);
        assertTrue(gui.lockButton("gate2").isLocked(),
                "the built seed reaches the cache");

        port.sink().controlEvent("gate2", "click",
                Map.of("id", "gate2", "type", "lockButton"));
        assertEquals(1, clicks.size());

        // The lockButtonBuilder(boolean) factory folds into construct([bool]).
        LockButtonBuilder seeded =
                (LockButtonBuilder) new LockButtonBuilder()
                        .construct(new Object[]{true});
        assertEquals(true, seeded.build().locked());
    }

    // ------------------------------------------------------------------
    // AbstractWidgetBuilder completed base face
    // ------------------------------------------------------------------

    @Test
    void builderBaseFaceCompletesLayoutAndAlpha() {
        ButtonBuilder builder = new ButtonBuilder()
                .id("b1").x(7).y(9).width(40).height(20)
                .alpha(0.5)
                .moveTo(3, 4);
        ControlSpec spec = builder.build();
        assertEquals(3, spec.x());
        assertEquals(4, spec.y());
        assertEquals(40, spec.width());
        assertEquals(20, spec.height());
        assertEquals(0.5, spec.alpha());

        // The layout reads answer the configured box before build.
        assertEquals(40, builder.getScaledWidth());
        assertEquals(20, builder.getScaledHeight());
        assertEquals(3, builder.getScaledLeft());
        assertEquals(4, builder.getScaledTop());
        assertEquals(640, builder.getParentWidth());
        assertEquals(360, builder.getParentHeight());

        // Alpha clamps to 0..1 and defaults to unset.
        assertEquals(1.0, new ButtonBuilder().alpha(2.0).build().alpha(), 1e-9);
        assertEquals(0.0, new ButtonBuilder().alpha(-1).build().alpha(), 1e-9);
        assertNull(new ButtonBuilder().build().alpha());

        // The alpha key rides the neutral form the adapter applies.
        assertEquals(0.5, ((Number) spec.toNeutral().get("alpha")).doubleValue(),
                1e-9);
    }
}
