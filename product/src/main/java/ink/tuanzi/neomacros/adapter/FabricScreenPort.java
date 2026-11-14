package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.adapter.mixin.CheckboxAccessor;
import ink.tuanzi.neomacros.fabric.ScriptScreen;
import ink.tuanzi.neomacros.port.ScreenPort;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.LockIconButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Screen sessions against the vanilla client: opens the declarative spec
 * as a {@link ScriptScreen} host on the main thread, builds the seven
 * vanilla control types from the neutral maps, routes widget events into
 * the neutral {@link SessionSink}, and drives close/reload/mutation hops.
 *
 * <p>Threading: mutations bridge onto the main thread through
 * {@link MainThread}; while a session callback is executing the main
 * thread is parked waiting on the script context loop, so the port's
 * dispatch gate lets those mutations run inline instead of timing out
 * against the parked thread. Reads answer directly (the same precedent as
 * the lifecycle screen reads).</p>
 */
public final class FabricScreenPort implements ScreenPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(FabricScreenPort.class);

    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /** One open session: neutral control mirrors, live widgets, host, parent. */
    private static final class Session {
        final long id;
        final SessionSink sink;
        final String returnTo;
        final Screen parent;
        /** Ordered neutral control maps (the port's spec mirror, adopt writes back). */
        final List<Map<String, Object>> controls = new CopyOnWriteArrayList<>();
        /** Live widget by control id (main thread writes; reads are field-safe). */
        final Map<String, AbstractWidget> widgets = new ConcurrentHashMap<>();
        volatile ScriptScreen host;
        volatile boolean notified;

        Session(long id, SessionSink sink, String returnTo, Screen parent,
                List<Map<String, Object>> controls) {
            this.id = id;
            this.sink = sink;
            this.returnTo = returnTo;
            this.parent = parent;
            this.controls.addAll(controls);
        }
    }

    @Override
    public Long open(Map<String, Object> normalizedSpec, SessionSink sink) {
        // Notify a previous script session before the main-thread hop: its
        // close callback needs the calling context loop, and that loop is
        // blocked while the open task runs (the notified flag makes the
        // in-task repeat a no-op).
        Screen previous = FabricLifecyclePort.currentScreen();
        if (previous instanceof ScriptScreen priorHost) {
            Session prior = sessionOf(priorHost);
            if (prior != null) {
                notifyClosed(prior);
            }
        }
        return runOnMain(() -> {
            Screen current = FabricLifecyclePort.currentScreen();
            Screen parent = current;
            if (current instanceof ScriptScreen oldHost) {
                Session prior = sessionOf(oldHost);
                if (prior != null) {
                    // Single-screen invariant: the new session inherits the
                    // previous session's captured parent; the pre-hop
                    // notification already fired (this repeat is a no-op).
                    parent = prior.parent;
                    notifyClosed(prior);
                }
            }
            String returnTo = normalizedSpec.get("returnTo") instanceof String text
                    && "game".equals(text) ? "game" : "current";
            if (!"current".equals(returnTo)) {
                parent = null;
            }
            String title = normalizedSpec.get("title") instanceof String text ? text : "";
            long id = sequence.incrementAndGet();
            Session session = new Session(id, sink, returnTo, parent,
                    controlsOf(normalizedSpec));
            ScriptScreen host = new ScriptScreen(this, id, sink, title, returnTo, parent);
            session.host = host;
            sessions.put(id, session);
            Minecraft.getInstance().gui.setScreen(host);
            return id;
        });
    }

    @Override
    public void close(long session) {
        Session s = sessions.get(session);
        if (s == null || s.host == null) {
            return;
        }
        // Same path Esc takes: close callback first, then the parent restore.
        s.host.requestClose();
    }

    /** Close intent driven by the host ({@code onClose}/Esc); runs the hop. */
    public void closeIntent(long session) {
        runOnMain(() -> {
            Session s = sessions.get(session);
            if (s == null) {
                return null;
            }
            notifyClosed(s);
            // Only restore when this session still owns the display (the
            // close callback may already have opened a replacement screen).
            if (FabricLifecyclePort.currentScreen() == s.host) {
                Minecraft.getInstance().gui.setScreen(
                        s.parent != null ? s.parent : null);
            }
            sessions.remove(session);
            return null;
        });
    }

    /** The host was replaced by another screen: notify only, no restore. */
    public void replaced(long session) {
        Session s = sessions.get(session);
        if (s == null) {
            return;
        }
        notifyClosed(s);
        sessions.remove(session);
    }

    @Override
    public void reload(long session) {
        runOnMain(() -> {
            Session s = sessions.get(session);
            if (s != null && s.host != null) {
                s.host.rebuild();
            }
            return null;
        });
    }

    @Override
    public void addControl(long session, Map<String, Object> control) {
        runOnMain(() -> {
            Session s = sessions.get(session);
            if (s != null && s.host != null) {
                s.controls.add(control);
                s.host.rebuild();
            }
            return null;
        });
    }

    @Override
    public void removeControl(long session, String id) {
        runOnMain(() -> {
            Session s = sessions.get(session);
            if (s != null && s.host != null) {
                s.controls.removeIf(control -> id.equals(control.get("id")));
                s.host.rebuild();
            }
            return null;
        });
    }

    /** Patch keys that are transient widget ops and never enter the mirror. */
    private static final java.util.Set<String> TRANSIENT_KEYS = java.util.Set.of(
            "cursor", "cursorTo", "shift", "selection");

    @Override
    public void writeControl(long session, String id, Map<String, Object> patch) {
        runOnMain(() -> {
            Session s = sessions.get(session);
            if (s == null) {
                return null;
            }
            Map<String, Object> control = controlOf(s, id);
            if (control == null) {
                return null;
            }
            // Mirror into the spec copy so the next rebuild keeps the write
            // (transient cursor/selection ops stay out of the mirror).
            for (Map.Entry<String, Object> entry : patch.entrySet()) {
                if (!TRANSIENT_KEYS.contains(entry.getKey())) {
                    control.put(entry.getKey(), entry.getValue());
                }
            }
            AbstractWidget widget = s.widgets.get(id);
            if (widget == null) {
                return null;
            }
            if (patch.get("label") instanceof String label) {
                widget.setMessage(Component.literal(label));
            }
            if (patch.get("active") instanceof Boolean active) {
                widget.active = active;
            }
            if (patch.get("visible") instanceof Boolean visible) {
                widget.visible = visible;
            }
            if (patch.get("checked") instanceof Boolean checked
                    && widget instanceof Checkbox box) {
                ((CheckboxAccessor) box).neomacros$setSelected(checked);
            }
            if (patch.get("value") instanceof Number value
                    && widget instanceof SliderWidget slider) {
                slider.writeValue(value.doubleValue());
            }
            if (patch.get("steps") instanceof Number steps
                    && widget instanceof SliderWidget slider) {
                slider.writeSteps(steps.intValue());
            }
            if (widget instanceof EditBox edit) {
                if (patch.get("text") instanceof String text) {
                    edit.setValue(text);
                }
                if (patch.get("editable") instanceof Boolean editable) {
                    edit.setEditable(editable);
                }
                if (patch.get("maxLength") instanceof Number length) {
                    edit.setMaxLength(length.intValue());
                }
                if (patch.get("suggestion") instanceof String suggestion) {
                    edit.setSuggestion(suggestion);
                }
                if (patch.get("textColor") instanceof Number color) {
                    edit.setTextColor(color.intValue());
                }
                if (patch.get("uneditableColor") instanceof Number color) {
                    edit.setTextColorUneditable(color.intValue());
                }
                if (patch.get("cursor") instanceof Number cursor) {
                    if (patch.get("shift") instanceof Boolean shift) {
                        edit.moveCursorTo(cursor.intValue(), shift);
                    } else {
                        edit.setCursorPosition(cursor.intValue());
                    }
                }
                if (patch.get("cursorTo") instanceof String to) {
                    boolean shift = Boolean.TRUE.equals(patch.get("shift"));
                    if ("start".equals(to)) {
                        edit.moveCursorToStart(shift);
                    } else {
                        edit.moveCursorToEnd(shift);
                    }
                }
                if (patch.get("selection") instanceof List<?> selection
                        && selection.size() == 2
                        && selection.get(0) instanceof Number start
                        && selection.get(1) instanceof Number end) {
                    edit.setHighlightPos(start.intValue());
                    edit.setCursorPosition(end.intValue());
                }
            } else if (patch.get("text") instanceof String text
                    && widget instanceof CycleButton<?> cycle) {
                @SuppressWarnings("unchecked")
                CycleButton<String> typed = (CycleButton<String>) cycle;
                typed.setValue(text);
            }
            if (patch.get("locked") instanceof Boolean locked
                    && widget instanceof LockIconButton lock) {
                lock.setLocked(locked);
            }
            // z is the ordering key only: it applies at the next rebuild.
            return null;
        });
    }

    @Override
    public Optional<ControlState> readControl(long session, String id) {
        Session s = sessions.get(session);
        if (s == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(stateOf(s, id));
    }

    @Override
    public boolean isDisplayed(long session) {
        Session s = sessions.get(session);
        return s != null && s.host != null
                && FabricLifecyclePort.currentScreen() == s.host;
    }

    //? if >=26.1 {
    @Override
    public boolean shiftDown() {
        return Minecraft.getInstance().hasShiftDown();
    }

    @Override
    public boolean controlDown() {
        return Minecraft.getInstance().hasControlDown();
    }

    @Override
    public boolean altDown() {
        return Minecraft.getInstance().hasAltDown();
    }
    //?} else {
    /*
    @Override
    public boolean shiftDown() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }

    @Override
    public boolean controlDown() {
        return net.minecraft.client.gui.screens.Screen.hasControlDown();
    }

    @Override
    public boolean altDown() {
        return net.minecraft.client.gui.screens.Screen.hasAltDown();
    }
    */
    //?}

    // ------------------------------------------------------------------
    // Host callbacks (init/adopt/rebuild) — main thread or gated inline
    // ------------------------------------------------------------------

    /** Builds the widget tree from the session's neutral control mirrors. */
    public void buildWidgets(ScriptScreen host, long session) {
        Session s = sessions.get(session);
        if (s == null) {
            return;
        }
        s.widgets.clear();
        List<Map<String, Object>> ordered = new ArrayList<>(s.controls);
        // Stable sort by the z ordering key; declaration order breaks ties.
        ordered.sort(Comparator.comparingInt(FabricScreenPort::zOf));
        for (Map<String, Object> control : ordered) {
            AbstractWidget widget = createWidget(s, host, control);
            if (widget == null) {
                continue;
            }
            Object id = control.get("id");
            if (id instanceof String key) {
                s.widgets.put(key, widget);
                host.attach(widget);
            }
        }
    }

    /**
     * Writes live widget state (text/value/checked/locked) back into the
     * control mirrors; called by the host right before the tree is torn
     * down so resize/reload/rebuild keep typed input.
     */
    public void adoptLiveState(long session) {
        Session s = sessions.get(session);
        if (s == null || s.widgets.isEmpty()) {
            return;
        }
        for (Map.Entry<String, AbstractWidget> entry : s.widgets.entrySet()) {
            Map<String, Object> control = controlOf(s, entry.getKey());
            if (control == null) {
                continue;
            }
            AbstractWidget widget = entry.getValue();
            if (widget instanceof EditBox edit) {
                control.put("text", edit.getValue());
            } else if (widget instanceof SliderWidget slider) {
                control.put("value", slider.currentValue());
            } else if (widget instanceof Checkbox box) {
                control.put("checked", box.selected());
            } else if (widget instanceof LockIconButton lock) {
                control.put("locked", lock.isLocked());
            } else if (widget instanceof CycleButton<?> cycle) {
                control.put("text", String.valueOf(cycle.getValue()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Control construction (G-003 §3.3: seven vanilla types)
    // ------------------------------------------------------------------

    private AbstractWidget createWidget(Session session, ScriptScreen host,
                                        Map<String, Object> control) {
        String id = str(control.get("id"));
        String type = str(control.get("type"));
        int x = intOf(control.get("x"));
        int y = intOf(control.get("y"));
        int width = intOf(control.get("width"));
        int height = intOf(control.get("height"));
        String label = str(control.get("label"));
        switch (type) {
            case "button" -> {
                Button button = Button.builder(Component.literal(label),
                                pressed -> click(session, id, type))
                        .bounds(x, y, width, height)
                        .build();
                applyCommon(button, control);
                return button;
            }
            case "imageButton" -> {
                WidgetSprites sprites = spritesOf(control, id);
                if (sprites == null) {
                    LOGGER.error("screen control '{}' skipped: bad sprites", id);
                    return null;
                }
                ImageButton image = new ImageButton(x, y, width, height, sprites,
                        pressed -> click(session, id, type));
                applyCommon(image, control);
                return image;
            }
            case "checkbox" -> {
                boolean checked = Boolean.TRUE.equals(control.get("checked"));
                Checkbox box = Checkbox.builder(Component.literal(label),
                                host.screenFont())
                        .pos(x, y)
                        .selected(checked)
                        .onValueChange((changed, value) -> {
                            Map<String, Object> payload = payload(id, type);
                            payload.put("checked", value);
                            session.sink.controlEvent(id, "change", payload);
                            session.sink.stateChanged(id, stateOf(session, id));
                        })
                        .build();
                applyCommon(box, control);
                return box;
            }
            case "slider" -> {
                double value = control.get("value") instanceof Number number
                        ? number.doubleValue() : 0.0;
                int steps = control.get("steps") instanceof Number number
                        ? number.intValue() : 0;
                SliderWidget slider = new SliderWidget(session, id, label,
                        x, y, width, height, value, steps);
                applyCommon(slider, control);
                return slider;
            }
            case "editBox" -> {
                FilteredEditBox edit = new FilteredEditBox(host.screenFont(),
                        x, y, width, height,
                        Component.literal(label != null ? label : ""),
                        session, id);
                if (control.get("text") instanceof String initial && !initial.isEmpty()) {
                    edit.setValue(initial);
                }
                edit.setResponder(text -> {
                    Map<String, Object> payload = payload(id, type);
                    payload.put("text", text);
                    session.sink.controlEvent(id, "change", payload);
                    session.sink.stateChanged(id, stateOf(session, id));
                });
                if (control.get("maxLength") instanceof Number maximum) {
                    edit.setMaxLength(maximum.intValue());
                }
                if (control.get("suggestion") instanceof String suggestion) {
                    edit.setSuggestion(suggestion);
                }
                if (control.get("editable") instanceof Boolean editable) {
                    edit.setEditable(editable);
                }
                if (control.get("textColor") instanceof Number color) {
                    edit.setTextColor(color.intValue());
                }
                if (control.get("uneditableColor") instanceof Number color) {
                    edit.setTextColorUneditable(color.intValue());
                }
                applyCommon(edit, control);
                return edit;
            }
            case "cycleButton" -> {
                Object rawValues = control.get("values");
                if (!(rawValues instanceof List<?> values) || values.isEmpty()) {
                    LOGGER.error("screen control '{}' skipped: cycleButton needs values", id);
                    return null;
                }
                List<String> primary = new ArrayList<>(values.size());
                for (Object value : values) {
                    primary.add(String.valueOf(value));
                }
                String prefix = str(control.get("prefix"));
                Function<String, Component> valueToText = value ->
                        Component.literal(prefix != null ? prefix + value : value);
                String initial = control.get("initial") instanceof String text
                        ? text : primary.get(0);
                // 1.21.6 seeds the initial value through the builder chain;
                // 26.x moved it into the builder() overload.
                //? if >=26.1 {
                CycleButton.Builder<String> builder =
                        CycleButton.builder(valueToText, initial);
                //?} else {
                /*
                CycleButton.Builder<String> builder =
                        CycleButton.builder(valueToText).withInitialValue(initial);
                */
                //?}
                if (control.get("alternatives") instanceof List<?> extra) {
                    List<String> secondary = new ArrayList<>(extra.size());
                    for (Object value : extra) {
                        secondary.add(String.valueOf(value));
                    }
                    builder.withValues(primary, secondary);
                } else {
                    builder.withValues(primary);
                }
                CycleButton<String> cycle = builder.create(x, y, width, height,
                        Component.literal(label != null ? label : ""),
                        (changed, value) -> {
                            Map<String, Object> payload = payload(id, type);
                            payload.put("value", value);
                            session.sink.controlEvent(id, "change", payload);
                            session.sink.stateChanged(id, stateOf(session, id));
                        });
                applyCommon(cycle, control);
                return cycle;
            }
            case "lockButton" -> {
                LockIconButton lock = new LockIconButton(x, y, pressed -> {
                    LockIconButton self = (LockIconButton) pressed;
                    self.setLocked(!self.isLocked());
                    click(session, id, type);
                    Map<String, Object> change = payload(id, type);
                    change.put("locked", self.isLocked());
                    session.sink.controlEvent(id, "change", change);
                    session.sink.stateChanged(id, stateOf(session, id));
                });
                if (Boolean.TRUE.equals(control.get("locked"))) {
                    lock.setLocked(true);
                }
                applyCommon(lock, control);
                return lock;
            }
            default -> {
                LOGGER.error("screen control '{}' has unknown type {}", id, type);
                return null;
            }
        }
    }

    private static void applyCommon(AbstractWidget widget, Map<String, Object> control) {
        if (control.get("active") instanceof Boolean active) {
            widget.active = active;
        }
        if (control.get("visible") instanceof Boolean visible) {
            widget.visible = visible;
        }
    }

    private static void click(Session session, String id, String type) {
        session.sink.controlEvent(id, "click", payload(id, type));
    }

    private static Map<String, Object> payload(String id, String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("type", type);
        return payload;
    }

    private static WidgetSprites spritesOf(Map<String, Object> control, String id) {
        if (!(control.get("sprites") instanceof Map<?, ?> sprites)) {
            return null;
        }
        Identifier[] parts = new Identifier[4];
        String[] keys = {"enabled", "disabled", "enabledFocused", "disabledFocused"};
        for (int i = 0; i < keys.length; i++) {
            Object value = sprites.get(keys[i]);
            if (!(value instanceof String texture)
                    || (parts[i] = Identifier.tryParse(texture)) == null) {
                return null;
            }
        }
        return new WidgetSprites(parts[0], parts[1], parts[2], parts[3]);
    }

    // ------------------------------------------------------------------
    // State snapshots and helpers
    // ------------------------------------------------------------------

    /** Builds one neutral control snapshot from the mirror plus live widget. */
    private static ControlState stateOf(Session session, String id) {
        Map<String, Object> control = controlOf(session, id);
        if (control == null) {
            return null;
        }
        String type = str(control.get("type"));
        int x = intOf(control.get("x"));
        int y = intOf(control.get("y"));
        int width = intOf(control.get("width"));
        int height = intOf(control.get("height"));
        int z = intOf(control.get("z"));
        String label = str(control.get("label"));
        Boolean checked = control.get("checked") instanceof Boolean checkedFlag
                ? checkedFlag : null;
        Double value = control.get("value") instanceof Number number
                ? number.doubleValue() : null;
        String text = control.get("text") instanceof String initial ? initial : null;
        Boolean locked = control.get("locked") instanceof Boolean lockedFlag
                ? lockedFlag : null;
        Integer steps = control.get("steps") instanceof Number number
                ? number.intValue() : null;
        Boolean editable = control.get("editable") instanceof Boolean editableFlag
                ? editableFlag : null;
        Integer maxLength = control.get("maxLength") instanceof Number number
                ? number.intValue() : null;
        boolean active = control.get("active") instanceof Boolean activeFlag
                ? activeFlag : true;
        boolean visible = control.get("visible") instanceof Boolean visibleFlag
                ? visibleFlag : true;
        String selectedText = null;

        AbstractWidget widget = session.widgets.get(id);
        if (widget != null) {
            x = widget.getX();
            y = widget.getY();
            width = widget.getWidth();
            height = widget.getHeight();
            active = widget.active;
            visible = widget.visible;
            if (widget instanceof Checkbox box) {
                checked = box.selected();
            } else if (widget instanceof SliderWidget slider) {
                value = slider.currentValue();
                steps = slider.currentSteps();
            } else if (widget instanceof EditBox edit) {
                text = edit.getValue();
                selectedText = edit.getHighlighted();
            } else if (widget instanceof LockIconButton lock) {
                locked = lock.isLocked();
            } else if (widget instanceof CycleButton<?> cycle) {
                text = String.valueOf(cycle.getValue());
            }
        }
        return new ControlState(id, type, x, y, width, height, z,
                label != null ? label : "", active, visible,
                checked, value, text, locked,
                steps, editable, maxLength, selectedText);
    }

    private static Map<String, Object> controlOf(Session session, String id) {
        for (Map<String, Object> control : session.controls) {
            if (id.equals(control.get("id"))) {
                return control;
            }
        }
        return null;
    }

    private Session sessionOf(ScriptScreen host) {
        for (Session session : sessions.values()) {
            if (session.host == host) {
                return session;
            }
        }
        return null;
    }

    private static void notifyClosed(Session session) {
        if (session.notified) {
            return;
        }
        session.notified = true;
        try {
            session.sink.closed();
        } catch (Throwable t) {
            LOGGER.error("screen close notification failed", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> controlsOf(Map<String, Object> spec) {
        Object value = spec.get("controls");
        List<Map<String, Object>> controls = new ArrayList<>();
        if (value instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> map) {
                    controls.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        return controls;
    }

    private static int zOf(Map<String, Object> control) {
        return intOf(control.get("z"));
    }

    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String str(Object value) {
        return value instanceof String text ? text : "";
    }

    /**
     * Runs one task on the main thread: inline when already there or while
     * a session callback dispatch has the main thread parked (the gate), a
     * blocking bridge hop otherwise. Failures log and answer null.
     */
    private <T> T runOnMain(Supplier<T> task) {
        if (ScreenPort.isDispatchActive() || Minecraft.getInstance().isSameThread()) {
            try {
                return task.get();
            } catch (Throwable t) {
                LOGGER.error("screen port task failed", t);
                return null;
            }
        }
        return MainThread.run(task);
    }

    /** Discrete/continuous slider with script change + state notifications. */
    private static final class SliderWidget extends AbstractSliderButton {

        private final Session session;
        private final String id;
        private final String label;
        private int steps;

        SliderWidget(Session session, String id, String label, int x, int y,
                     int width, int height, double value, int steps) {
            super(x, y, width, height, Component.literal(""), value);
            this.session = session;
            this.id = id;
            this.label = label != null ? label : "";
            this.steps = Math.max(0, steps);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(steps > 0
                    ? label + " " + Math.round(value * steps) + "/" + steps
                    : label + " " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            if (steps > 0) {
                value = Math.round(value * steps) / (double) steps;
                updateMessage();
            }
            Map<String, Object> payload = payload(id, "slider");
            payload.put("value", value);
            session.sink.controlEvent(id, "change", payload);
            session.sink.stateChanged(id, stateOf(session, id));
        }

        double currentValue() {
            return value;
        }

        int currentSteps() {
            return steps;
        }

        /** Script write path: updates without echoing a change event. */
        void writeValue(double next) {
            value = Math.max(0.0, Math.min(1.0, next));
            if (steps > 0) {
                value = Math.round(value * steps) / (double) steps;
            }
            updateMessage();
        }

        /** Script write path: re-snaps the value onto the new grid. */
        void writeSteps(int next) {
            this.steps = Math.max(0, next);
            if (steps > 0) {
                value = Math.round(value * steps) / (double) steps;
            }
            updateMessage();
        }
    }

    /**
     * Edit box with the G-036 script text-filter gate: user input flows
     * through {@link #insertText}, which asks the session's
     * {@code filterText} with the candidate full text (computed exactly
     * like vanilla: selection range replaced by the fragment) before
     * delegating. 1.21.6's native {@code setFilter} stays untouched (its
     * constructor default accepts everything); 26.x dropped the native
     * filter entirely (javap), so this override is the uniform gate on
     * every node. Script-initiated {@code setValue} writes bypass it.
     *
     * <p>The selection anchor is tracked through {@code setHighlightPos}
     * — bytecode shows it is the only writer of {@code highlightPos} on
     * both nodes.</p>
     */
    private static final class FilteredEditBox extends EditBox {

        private final Session session;
        private final String id;
        private int highlight;

        FilteredEditBox(net.minecraft.client.gui.Font font, int x, int y,
                        int width, int height, Component label,
                        Session session, String id) {
            super(font, x, y, width, height, label);
            this.session = session;
            this.id = id;
        }

        @Override
        public void setHighlightPos(int position) {
            super.setHighlightPos(position);
            highlight = position;
        }

        @Override
        public void insertText(String text) {
            String value = getValue();
            int cursor = getCursorPosition();
            int start = Math.min(cursor, highlight);
            int end = Math.max(cursor, highlight);
            String candidate = value.substring(0, start) + text
                    + value.substring(end);
            if (!session.sink.filterText(id, candidate)) {
                return;
            }
            super.insertText(text);
        }
    }
}
