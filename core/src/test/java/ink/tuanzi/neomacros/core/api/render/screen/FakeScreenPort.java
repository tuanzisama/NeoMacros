package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.DialogPort;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.InputPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.ScreenPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.DrawContext2d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Recording fake screen port for the core screen tests: captures the
 * neutral spec at open, records every mutation, seeds control states, and
 * lets tests drive the session sink directly (the stub-driven e2e twin).
 */
final class FakeScreenPort implements ScreenPort {

    volatile boolean failOpen;
    volatile boolean shift;
    volatile boolean control;
    volatile boolean alt;

    final List<String> actions = new CopyOnWriteArrayList<>();
    final List<Map<String, Object>> patches = new CopyOnWriteArrayList<>();
    final List<Map<String, Object>> neutralSpecs = new ArrayList<>();
    final Map<Long, SessionSink> sinks = new ConcurrentHashMap<>();
    final Map<Long, Map<String, ControlState>> states = new ConcurrentHashMap<>();
    final List<String> drawn = new CopyOnWriteArrayList<>();

    private long nextId = 1;
    private volatile long active = -1;

    @Override
    public Long open(Map<String, Object> normalizedSpec, SessionSink sink) {
        actions.add("open");
        if (failOpen) {
            return null;
        }
        long id = nextId++;
        sinks.put(id, sink);
        neutralSpecs.add(normalizedSpec);
        Map<String, ControlState> seeded = new LinkedHashMap<>();
        if (normalizedSpec.get("controls") instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    ControlState state = stateOf((Map<String, Object>) map);
                    seeded.put(state.id(), state);
                }
            }
        }
        states.put(id, seeded);
        active = id;
        return id;
    }

    @Override
    public void close(long session) {
        actions.add("close:" + session);
        SessionSink sink = sinks.get(session);
        if (sink != null) {
            sink.closed();
        }
        if (active == session) {
            active = -1;
        }
    }

    @Override
    public void reload(long session) {
        actions.add("reload:" + session);
    }

    @Override
    public void addControl(long session, Map<String, Object> control) {
        actions.add("add:" + session + ":" + control.get("id"));
        Map<String, ControlState> sessionStates = states.get(session);
        if (sessionStates != null) {
            ControlState state = stateOf(control);
            sessionStates.put(state.id(), state);
        }
    }

    @Override
    public void removeControl(long session, String id) {
        actions.add("remove:" + session + ":" + id);
        Map<String, ControlState> sessionStates = states.get(session);
        if (sessionStates != null) {
            sessionStates.remove(id);
        }
    }

    @Override
    public void writeControl(long session, String id, Map<String, Object> patch) {
        actions.add("write:" + session + ":" + id);
        patches.add(new LinkedHashMap<>(patch));
        Map<String, ControlState> sessionStates = states.get(session);
        ControlState state = sessionStates == null ? null : sessionStates.get(id);
        if (state == null) {
            return;
        }
        sessionStates.put(id, new ControlState(state.id(), state.type(), state.x(),
                state.y(), state.width(), state.height(),
                patch.get("z") instanceof Number z ? z.intValue() : state.z(),
                patch.get("label") instanceof String label ? label : state.label(),
                patch.get("active") instanceof Boolean a ? a : state.active(),
                patch.get("visible") instanceof Boolean v ? v : state.visible(),
                patch.get("checked") instanceof Boolean c ? c : state.checked(),
                patch.get("value") instanceof Number n ? n.doubleValue() : state.value(),
                patch.get("text") instanceof String t ? t : state.text(),
                patch.get("locked") instanceof Boolean l ? l : state.locked(),
                patch.get("steps") instanceof Number s ? s.intValue() : state.steps(),
                patch.containsKey("editable") && patch.get("editable") instanceof Boolean e
                        ? e : state.editable(),
                patch.get("maxLength") instanceof Number m ? m.intValue()
                        : state.maxLength(),
                state.selectedText()));
    }

    @Override
    public Optional<ControlState> readControl(long session, String id) {
        Map<String, ControlState> sessionStates = states.get(session);
        return sessionStates == null ? Optional.empty()
                : Optional.ofNullable(sessionStates.get(id));
    }

    @Override
    public boolean isDisplayed(long session) {
        return active == session && sinks.containsKey(session);
    }

    @Override
    public boolean shiftDown() {
        return shift;
    }

    @Override
    public boolean controlDown() {
        return control;
    }

    @Override
    public boolean altDown() {
        return alt;
    }

    /** The most recently opened session's sink (tests drive it directly). */
    SessionSink sink() {
        long last = nextId - 1;
        return last >= 1 ? sinks.get(last) : null;
    }

    /** The active session id, or -1. */
    long activeSession() {
        return active;
    }

    /** Records one draw through the render callback's canvas. */
    DrawContext2d recordingCanvas() {
        return new DrawContext2d() {
            @Override
            public void text(int x, int y, String text) {
                drawn.add("text:" + text);
            }

            @Override
            public void text(int x, int y, String text, int color) {
                drawn.add("text:" + text);
            }

            @Override
            public void rect(int x, int y, int width, int height, int color) {
                drawn.add("rect:" + width + "x" + height);
            }

            @Override
            public void outline(int x, int y, int width, int height, int color) {
                drawn.add("outline");
            }

            @Override
            public void sprite(int x, int y, int width, int height, String textureId) {
                drawn.add("sprite:" + textureId);
            }
        };
    }

    private static ControlState stateOf(Map<String, Object> control) {
        String id = control.get("id") instanceof String text ? text : "";
        String type = control.get("type") instanceof String text ? text : "";
        return new ControlState(id, type,
                control.get("x") instanceof Number n ? n.intValue() : 0,
                control.get("y") instanceof Number n ? n.intValue() : 0,
                control.get("width") instanceof Number n ? n.intValue() : 0,
                control.get("height") instanceof Number n ? n.intValue() : 0,
                control.get("z") instanceof Number n ? n.intValue() : 0,
                control.get("label") instanceof String text ? text : "",
                !(control.get("active") instanceof Boolean a) || a,
                !(control.get("visible") instanceof Boolean v) || v,
                control.get("checked") instanceof Boolean c ? c : null,
                control.get("value") instanceof Number n ? n.doubleValue() : null,
                control.get("text") instanceof String text ? text : null,
                control.get("locked") instanceof Boolean l ? l : null,
                control.get("steps") instanceof Number n ? n.intValue() : null,
                control.get("editable") instanceof Boolean e ? e : null,
                control.get("maxLength") instanceof Number n ? n.intValue() : null,
                null);
    }

    /** Adapter exposing only the screen port; every other port throws. */
    record ScreenOnlyAdapter(FakeScreenPort screenPort) implements NeomacrosAdapter {
        @Override
        public LifecyclePort lifecycle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorldPort world() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityPort entity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlayerPort player() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientPort client() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InventoryPort inventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DialogPort dialog() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPort render() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Draw3DPort draw3d() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScreenPort screen() {
            return screenPort;
        }
    }
}
