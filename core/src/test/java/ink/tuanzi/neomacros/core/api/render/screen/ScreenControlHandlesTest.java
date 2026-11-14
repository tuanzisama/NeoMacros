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
 * G-036: the four basic live control handles (Checkbox/Slider/EditBox plus
 * the Button/ImageButton bindings), their state/callback round-trips over
 * the fake port, the typed-accessor rules (null when closed/unknown, throw
 * on type mismatch), the edit-box text-filter channel, and the builder
 * family (chain → {@code build()} spec fields → {@code gui.add} layout).
 */
class ScreenControlHandlesTest {

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

    private BuiltScreen openControls() {
        return api.buildScreen(Map.of(
                "title", "T",
                "controls", List.of(
                        Map.of("type", "button", "id", "buy", "x", 10, "y", 40,
                                "width", 98, "height", 20, "label", "Buy"),
                        Map.of("type", "imageButton", "id", "icon", "x", 1, "y", 2,
                                "width", 16, "height", 16,
                                "sprites", Map.of(
                                        "enabled", "a:b", "disabled", "a:b",
                                        "enabledFocused", "a:b",
                                        "disabledFocused", "a:b")),
                        Map.of("type", "checkbox", "id", "dbg", "x", 10, "y", 70,
                                "width", 98, "height", 20, "checked", false),
                        Map.of("type", "slider", "id", "amt", "x", 10, "y", 100,
                                "width", 98, "height", 20, "value", 0.5, "steps", 4),
                        Map.of("type", "editBox", "id", "name", "x", 10, "y", 130,
                                "width", 98, "height", 20, "text", "hi",
                                "maxLength", 8, "editable", true))));
    }

    // ------------------------------------------------------------------
    // Typed accessors
    // ------------------------------------------------------------------

    @Test
    void typedAccessorsBindByTypeAndFailSoftOnUnknownOrClosed() {
        BuiltScreen gui = openControls();

        assertNotNull(gui.button("buy"));
        assertTrue(gui.button("icon") instanceof ImageButton,
                "imageButton binds the ImageButton subtype");
        assertNotNull(gui.checkbox("dbg"));
        assertNotNull(gui.slider("amt"));
        assertNotNull(gui.editBox("name"));

        assertNull(gui.button("missing"), "unknown id answers null");
        assertThrows(IllegalArgumentException.class, () -> gui.checkbox("buy"),
                "type mismatch throws");
        assertThrows(IllegalArgumentException.class, () -> gui.slider("name"));

        gui.close();
        assertNull(gui.checkbox("dbg"), "closed session answers null");
        Checkbox dead = new Checkbox(gui, "dbg");
        assertThrows(IllegalStateException.class, () -> dead.setChecked(true),
                "a handle kept across close cannot mutate");
        assertFalse(dead.isChecked(), "reads degrade to defaults after close");
    }

    // ------------------------------------------------------------------
    // Per-control state + callback assertions
    // ------------------------------------------------------------------

    @Test
    void checkboxStateAndToggleRoundTrip() {
        BuiltScreen gui = openControls();
        Checkbox box = gui.checkbox("dbg");

        assertFalse(box.isChecked());
        box.toggle();
        assertTrue(box.isChecked(), "toggle writes through the cache");
        assertEquals(true, gui.control("dbg").checked());

        box.setChecked(false);
        assertFalse(box.isChecked());
        assertTrue(port.actions.contains("write:1:dbg"));
        assertEquals(false, port.patches.get(port.patches.size() - 1).get("checked"));

        // The change callback fires from the adapter's control event.
        List<Object> changes = new CopyOnWriteArrayList<>();
        api.buildScreen(Map.of("controls", List.of(
                Map.of("type", "checkbox", "id", "c1", "checked", false,
                        "onChange", (java.util.function.Consumer<Object[]>)
                                args -> changes.add(args[0])))));
        port.sink().controlEvent("c1", "change",
                Map.of("id", "c1", "type", "checkbox", "checked", true));
        assertEquals(1, changes.size());
        assertEquals(true,
                ((Map<?, ?>) changes.get(0)).get("checked"));
    }

    @Test
    void sliderValueAndStepsReadWrite() {
        BuiltScreen gui = openControls();
        Slider slider = gui.slider("amt");

        assertEquals(0.5, slider.getValue(), 1e-9);
        assertEquals(4, slider.getSteps());

        slider.setValue(0.25);
        assertEquals(0.25, slider.getValue(), 1e-9);
        assertEquals(0.25, gui.control("amt").value(), 1e-9);

        slider.setSteps(10);
        assertEquals(10, slider.getSteps());
        assertEquals(10, gui.control("amt").steps());

        // The drag callback carries the live value.
        List<Object> changes = new CopyOnWriteArrayList<>();
        api.buildScreen(Map.of("controls", List.of(
                Map.of("type", "slider", "id", "s1", "value", 0.0,
                        "onChange", (java.util.function.Consumer<Object[]>)
                                args -> changes.add(args[0])))));
        port.sink().controlEvent("s1", "change",
                Map.of("id", "s1", "type", "slider", "value", 0.75));
        assertEquals(1, changes.size());
        assertEquals(0.75, ((Number) ((Map<?, ?>) changes.get(0)).get("value")).doubleValue());
    }

    @Test
    void editBoxTextEditableMaxLengthAndSelection() {
        BuiltScreen gui = openControls();
        EditBox edit = gui.editBox("name");

        assertEquals("hi", edit.getValue());
        assertTrue(edit.isEditable());
        assertEquals(8, edit.getMaxLength());

        edit.setValue("yo");
        assertEquals("yo", edit.getValue());

        edit.setEditable(false);
        assertFalse(edit.isEditable());
        edit.setMaxLength(16);
        assertEquals(16, edit.getMaxLength());

        edit.setSelection(0, 2);
        Map<String, Object> last = port.patches.get(port.patches.size() - 1);
        assertTrue(last.containsKey("selection"), "selection is a transient op");

        edit.setCursorPosition(1, null);
        last = port.patches.get(port.patches.size() - 1);
        assertEquals(1, last.get("cursor"));
        assertFalse(last.containsKey("shift"), "omitted shift stays absent");

        edit.setCursorPosition(2, true);
        last = port.patches.get(port.patches.size() - 1);
        assertEquals(2, last.get("cursor"));
        assertEquals(true, last.get("shift"));

        edit.moveCursorToStart(null);
        last = port.patches.get(port.patches.size() - 1);
        assertEquals("start", last.get("cursorTo"));
        edit.moveCursorToEnd(true);
        last = port.patches.get(port.patches.size() - 1);
        assertEquals("end", last.get("cursorTo"));
        assertEquals(true, last.get("shift"));

        // The live selection arrives through stateChanged.
        port.sink().stateChanged("name", new ink.tuanzi.neomacros.port.ScreenPort.ControlState(
                "name", "editBox", 10, 130, 98, 20, 0, "", true, true,
                null, null, "hello", null, null, true, 8, "hel"));
        assertEquals("hel", edit.getHighlighted());

        // The text callback carries the typed value.
        List<Object> changes = new CopyOnWriteArrayList<>();
        api.buildScreen(Map.of("controls", List.of(
                Map.of("type", "editBox", "id", "e1",
                        "onChange", (java.util.function.Consumer<Object[]>)
                                args -> changes.add(args[0])))));
        port.sink().controlEvent("e1", "change",
                Map.of("id", "e1", "type", "editBox", "text", "typed"));
        assertEquals(1, changes.size());
        assertEquals("typed", ((Map<?, ?>) changes.get(0)).get("text"));
    }

    @Test
    void buttonAndImageButtonCarryClickCallbacks() {
        List<Object> clicks = new CopyOnWriteArrayList<>();
        BuiltScreen gui = api.buildScreen(Map.of("controls", List.of(
                Map.of("type", "button", "id", "buy", "x", 0, "y", 0,
                        "width", 20, "height", 20,
                        "onClick", (java.util.function.Consumer<Object[]>)
                                args -> clicks.add(args[0])),
                Map.of("type", "imageButton", "id", "icon", "x", 0, "y", 0,
                        "width", 16, "height", 16,
                        "sprites", Map.of("enabled", "a:b", "disabled", "a:b",
                                "enabledFocused", "a:b", "disabledFocused", "a:b"),
                        "onClick", (java.util.function.Consumer<Object[]>)
                                args -> clicks.add(args[0])))));

        port.sink().controlEvent("buy", "click",
                Map.of("id", "buy", "type", "button"));
        port.sink().controlEvent("icon", "click",
                Map.of("id", "icon", "type", "imageButton"));

        assertEquals(2, clicks.size());
        assertEquals("buy", ((Map<?, ?>) clicks.get(0)).get("id"));
        assertEquals("icon", ((Map<?, ?>) clicks.get(1)).get("id"));
        assertNotNull(gui.button("buy"));
        assertNotNull(gui.button("icon"));
    }

    // ------------------------------------------------------------------
    // Text filter channel (setTextPredicate → SessionSink.filterText)
    // ------------------------------------------------------------------

    @Test
    void textFilterAcceptsUnlessExplicitlyFalseAndFailsOpenOnError() {
        BuiltScreen gui = openControls();
        EditBox edit = gui.editBox("name");

        assertTrue(port.sink().filterText("name", "anything"),
                "no predicate installed accepts");

        edit.setFilter(candidate -> candidate.length() <= 5);
        assertTrue(port.sink().filterText("name", "ok"));
        assertFalse(port.sink().filterText("name", "way-too-long"),
                "an explicit false rejects the candidate");

        edit.setFilter(candidate -> {
            throw new IllegalStateException("boom");
        });
        assertTrue(port.sink().filterText("name", "still-enters"),
                "a throwing predicate fails open");

        edit.resetFilter();
        assertTrue(port.sink().filterText("name", "anything-again"));

        // Removing the control drops its predicate too.
        edit.setFilter(candidate -> false);
        gui.remove("name");
        assertTrue(port.sink().filterText("name", "gone"),
                "a removed control has no predicate");
    }

    // ------------------------------------------------------------------
    // Builders: chain → build() spec → gui.add (layout + callbacks)
    // ------------------------------------------------------------------

    @Test
    void buttonBuilderProducesALaidOutSpecWithItsCallback() {
        List<Object> clicks = new CopyOnWriteArrayList<>();
        ButtonBuilder builder = new ButtonBuilder()
                .id("buyNow")
                .pos(10, 40)
                .size(98, 20)
                .zIndex(2)
                .message("Buy")
                .active(true)
                .onClick(args -> clicks.add(args[0]));

        ControlSpec spec = builder.build();
        assertEquals("button", spec.type());
        assertEquals("buyNow", spec.id());
        assertEquals(10, spec.x());
        assertEquals(40, spec.y());
        assertEquals(98, spec.width());
        assertEquals(20, spec.height());
        assertEquals(2, spec.z());
        assertEquals("Buy", spec.label());
        assertNotNull(spec.onClick());

        BuiltScreen gui = openControls();
        gui.add(spec);
        assertTrue(port.actions.contains("add:1:buyNow"));

        // height/size stay chainable after the base-class fold.
        assertEquals(20, new ButtonBuilder().height(20).build().height());
        ControlSpec sized = new ButtonBuilder().size(60, 30).build();
        assertEquals(60, sized.width());
        assertEquals(30, sized.height());

        // The id auto-generates when omitted.
        assertTrue(new ButtonBuilder().build().id().startsWith("ctl-"));
    }

    @Test
    void texturedButtonBuilderAssemblesTheSpriteQuadruple() {
        TexturedButtonBuilder partial = new TexturedButtonBuilder()
                .enabledTexture("gui/widgets");
        assertThrows(IllegalStateException.class, partial::build,
                "all four textures are required");

        TexturedButtonBuilder full = new TexturedButtonBuilder()
                .id("icon")
                .pos(1, 2)
                .size(16, 16)
                .enabledTexture("gui/on")
                .disabledTexture("gui/off")
                .enabledFocusedTexture("gui/on_focus")
                .disabledFocusedTexture("gui/off_focus");
        ControlSpec spec = full.build();
        assertEquals("imageButton", spec.type());
        assertNotNull(spec.sprites());
        assertEquals(4, spec.sprites().size());
        assertEquals("gui/on", spec.sprites().get("enabled"));
        assertEquals("gui/off_focus", spec.sprites().get("disabledFocused"));
    }

    @Test
    void checkBoxSliderAndTextFieldBuildersSeedTheirSpecFields() {
        CheckBoxBuilder checkBox = new CheckBoxBuilder()
                .id("dbg").pos(0, 0).size(98, 20)
                .checked(true)
                .onChange(args -> { });
        ControlSpec check = checkBox.build();
        assertEquals("checkbox", check.type());
        assertEquals(true, check.checked());
        assertNotNull(check.onChange());

        // The checkBoxBuilder(boolean) factory folds to a checked seed.
        assertEquals(true, ((CheckBoxBuilder) new CheckBoxBuilder()
                .construct(new Object[]{true})).build().checked());

        SliderBuilder slider = new SliderBuilder()
                .id("amt").pos(1, 2).size(98, 20)
                .steps(5)
                .initially(0.25);
        ControlSpec sliderSpec = slider.build();
        assertEquals("slider", sliderSpec.type());
        assertEquals(5, sliderSpec.steps());
        assertEquals(0.25, sliderSpec.value(), 1e-9);
        assertEquals(1, sliderSpec.x());
        assertEquals(2, sliderSpec.y());
        assertEquals(98, sliderSpec.width());
        assertEquals(20, sliderSpec.height());

        TextFieldBuilder field = new TextFieldBuilder()
                .id("name").pos(3, 4).size(98, 20)
                .suggestion("Type here");
        ControlSpec fieldSpec = field.build();
        assertEquals("editBox", fieldSpec.type());
        assertEquals("Type here", fieldSpec.suggestion());
        assertEquals(3, fieldSpec.x());
        assertEquals(4, fieldSpec.y());
    }

    @Test
    void builderSpecsRoundTripThroughAddAndStayTyped() {
        BuiltScreen gui = openControls();
        gui.add(new CheckBoxBuilder().id("extra").pos(10, 160).size(98, 20)
                .checked(true).build());
        gui.add(new TextFieldBuilder().id("note").pos(10, 190).size(98, 20)
                .build());

        Checkbox extra = gui.checkbox("extra");
        assertNotNull(extra);
        assertTrue(extra.isChecked(), "the built seed reaches the cache");
        assertNotNull(gui.editBox("note"));

        // Removing drops the typed binding to null.
        gui.remove("extra");
        assertNull(gui.checkbox("extra"));
    }
}
