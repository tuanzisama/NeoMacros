package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.DrawContext2d;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Script-built screen sessions: opening a declarative screen spec, driving
 * its lifecycle (close/reload/control mutations), and the neutral session
 * sink that carries render/input/control callbacks back to core. The port
 * never sees guest callback functions — adapters only ever invoke the
 * {@link SessionSink} methods with neutral data.
 *
 * <p>Screen reads (the currently open screen snapshot) stay on
 * {@link LifecyclePort}; this port is the write face only. Every mutating
 * call runs on the game main thread (internally bridged); while a session
 * callback is executing the main thread is parked waiting on the script
 * context loop, so the dispatch gate below lets mutations run inline
 * instead of deadlocking on the bridge.</p>
 *
 * @zh 脚本自建屏幕会话：以声明式 spec 开屏、驱动生命周期（关闭/重载/控件变更），以及把渲染/输入/控件回调带回 core 的中立会话接收器。端口永远见不到 guest 回调函数——adapter 只以中立数据调用 {@link SessionSink}。屏幕读面留在 {@link LifecyclePort}，本端口只管写面。
 */
public interface ScreenPort {

    /**
     * Control state snapshot (read face of one control). The four trailing
     * fields are G-036 additions: {@code steps} (slider discretization),
     * {@code editable}/{@code maxLength} (edit box configuration, script-
     * written so the mirror is authoritative), and {@code selectedText}
     * (live widget selection, read-only).
     */
    record ControlState(String id, String type, int x, int y, int width, int height, int z,
                        String label, boolean active, boolean visible,
                        Boolean checked, Double value, String text, Boolean locked,
                        Integer steps, Boolean editable, Integer maxLength,
                        String selectedText) {
    }

    /** One neutral mouse input (button press/drag/release). */
    record MouseInput(int button, double x, double y, double dx, double dy,
                      List<String> modifiers, Boolean doubleClick) {
    }

    /** One neutral key input (translation key, GLFW code, modifiers). */
    record KeyInput(String key, int code, List<String> modifiers) {
    }

    /** One neutral character input. */
    record CharInput(String character) {
    }

    /**
     * Session dispatch channel: registered at open; the adapter only ever
     * calls these with neutral data, so guest functions stay inside core.
     * Input methods answer {@code true} when the script consumed the event
     * (the adapter propagates that as vanilla's "handled" verdict);
     * {@link #closed()} fires exactly once per session on every close path.
     */
    interface SessionSink {
        /** Draws the script's {@code onRender} layer (topmost, per frame). */
        void render(DrawContext2d ctx, double mouseX, double mouseY, float partialTick);

        /** Screen-level mouse press; true = consumed. */
        boolean mouseDown(MouseInput e);

        /** Screen-level mouse drag; true = consumed. */
        boolean mouseDrag(MouseInput e);

        /** Screen-level mouse release; true = consumed. */
        boolean mouseUp(MouseInput e);

        /** Screen-level key press (after Esc/widgets); true = consumed. */
        boolean keyPress(KeyInput e);

        /** Screen-level char typed; true = consumed. */
        boolean charTyped(CharInput e);

        /** One control interaction ({@code click}/{@code change}). */
        void controlEvent(String controlId, String event, Map<String, Object> payload);

        /** Live control state changed inside the widget (text/value/checked...). */
        void stateChanged(String controlId, ControlState state);

        /**
         * Text-filter gate for one edit box (G-036): the adapter calls this
         * with the candidate full text before accepting a user edit; the
         * facade runs the script's {@code setFilter} predicate and answers
         * whether the text may enter the widget. Only neutral data crosses;
         * the default accepts everything (no predicate registered).
         * Script-initiated writes go through {@link #writeControl} and never
         * consult this gate.
         */
        default boolean filterText(String controlId, String candidate) {
            return true;
        }

        /**
         * Alternate-list toggle for one cycling button (G-036B): the
         * adapter consults this when the control was built with a script
         * {@code alternateToggle} and asks which value list the next cycle
         * uses ({@code true} = the alternate list). Only neutral data
         * crosses; the default answers {@code false} (the default list),
         * and a control without a registered toggle never reaches this
         * channel (the adapter keeps the vanilla Alt-key selector there).
         */
        default boolean alternateToggle(String controlId) {
            return false;
        }

        /** Session reached its terminal state; idempotent across all paths. */
        void closed();
    }

    /**
     * Dispatch gate: core opens it (depth-counted, re-entry safe) around
     * every guest callback invocation. While any gate is open the main
     * thread is parked waiting on a context loop, so port mutations may run
     * inline on the caller thread instead of bridging (which would time out
     * against the parked main thread).
     */
    AtomicLong DISPATCH_DEPTH = new AtomicLong(0L);

    /** Marks a session callback dispatch in progress (nesting increments). */
    static void beginDispatch() {
        DISPATCH_DEPTH.incrementAndGet();
    }

    /** Ends one dispatch marked by {@link #beginDispatch()}. */
    static void endDispatch() {
        DISPATCH_DEPTH.updateAndGet(depth -> depth > 0 ? depth - 1 : 0);
    }

    /** True while any session callback dispatch is in flight. */
    static boolean isDispatchActive() {
        return DISPATCH_DEPTH.get() > 0;
    }

    /**
     * Opens one screen session on the main thread from a neutral spec map
     * ({@code title/showTitle/pause/closeOnEsc/returnTo/controls}); callbacks
     * never appear in the map. Returns the session id, or {@code null} when
     * the bridge is unavailable or times out (the facade normalizes that to
     * a synchronous throw).
     */
    Long open(Map<String, Object> normalizedSpec, SessionSink sink);

    /** Closes the session (close intent: sink.closed() then restore parent). */
    void close(long session);

    /** Adopts live widget state and rebuilds the control tree (same session). */
    void reload(long session);

    /** Appends one control (neutral spec map, no callbacks) and rebuilds. */
    void addControl(long session, Map<String, Object> control);

    /** Removes one control by id and rebuilds. */
    void removeControl(long session, String id);

    /** Applies a whitelisted state patch to one live control (main thread). */
    void writeControl(long session, String id, Map<String, Object> patch);

    /** Reads one control's live state (cache-miss path), or empty. */
    Optional<ControlState> readControl(long session, String id);

    /** Whether the session's screen is the currently displayed one. */
    boolean isDisplayed(long session);

    /** Whether Shift is held (version-specific lookup behind the port). */
    boolean shiftDown();

    /** Whether Ctrl/Cmd is held. */
    boolean controlDown();

    /** Whether Alt is held. */
    boolean altDown();

    /**
     * The unsupported stand-in handed to adapters written before this port:
     * every mutating call raises {@link UnsupportedPortFeature}, reads
     * answer empty/false, and {@link #open} fails so the facade throws a
     * catchable script error instead of crashing.
     */
    ScreenPort NONE = new ScreenPort() {
        @Override
        public Long open(Map<String, Object> normalizedSpec, SessionSink sink) {
            throw new UnsupportedPortFeature("screen.open");
        }

        @Override
        public void close(long session) {
            throw new UnsupportedPortFeature("screen.close");
        }

        @Override
        public void reload(long session) {
            throw new UnsupportedPortFeature("screen.reload");
        }

        @Override
        public void addControl(long session, Map<String, Object> control) {
            throw new UnsupportedPortFeature("screen.addControl");
        }

        @Override
        public void removeControl(long session, String id) {
            throw new UnsupportedPortFeature("screen.removeControl");
        }

        @Override
        public void writeControl(long session, String id, Map<String, Object> patch) {
            throw new UnsupportedPortFeature("screen.writeControl");
        }

        @Override
        public Optional<ControlState> readControl(long session, String id) {
            return Optional.empty();
        }

        @Override
        public boolean isDisplayed(long session) {
            return false;
        }

        @Override
        public boolean shiftDown() {
            return false;
        }

        @Override
        public boolean controlDown() {
            return false;
        }

        @Override
        public boolean altDown() {
            return false;
        }
    };
}
