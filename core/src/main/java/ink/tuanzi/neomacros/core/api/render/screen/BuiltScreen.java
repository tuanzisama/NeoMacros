package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.event.SyncWatchdog;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.core.api.types.Pos2D;
import ink.tuanzi.neomacros.port.ScreenPort;
import ink.tuanzi.neomacros.port.model.DrawContext2d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Session handle of one screen built through {@code player.buildScreen}:
 * the write face over the control tree (add/remove/setControl), runtime
 * callback swaps ({@code gui.on}), reload/close, and the live reads
 * ({@code title/name/closed/controls/shiftDown/...}). One handle = one
 * {@code buildScreen} call; any close path (Esc, {@code gui.close()},
 * replacement by another screen, owner unload) pushes it to {@code closed}
 * and fires the {@code close} callback exactly once.
 *
 * <p>Callback dispatch is synchronous on the caller thread with a watchdog
 * budget; a callback that throws is swallowed and reads as "not consumed".
 * Input callbacks registered through {@link #on} answer {@code true} when
 * the script returns {@code true} (truthy = consumed); callbacks seeded in
 * the spec object run through the engine's {@link Consumer} channel, which
 * drops their return value, so consuming handlers belong on
 * {@link #on(String, Predicate)}.</p>
 *
 * <p>After {@code closed} every mutator throws, {@code controls} reads back
 * as empty, and plain reads ({@code title/name/closed}) keep answering.</p>
 *
 * @zh 经 {@code player.buildScreen} 构建的一次屏幕会话句柄：控件树写面（add/remove/setControl）、运行期回调换装（{@code gui.on}）、reload/close 与活读（{@code title/name/closed/controls/shiftDown/...}）。一个句柄 = 一次 {@code buildScreen}；任何关闭路径（Esc、{@code gui.close()}、被其他屏替换、owner 卸载）都会推到 {@code closed} 且 {@code close} 回调恰好一次。
 */
@ApiClass(name = "BuiltScreen", kind = ApiClassKind.HANDLE,
        wraps = "net.minecraft.client.gui.screens.Screen")
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public final class BuiltScreen implements ScriptHandle {

    /** The host class name reported by {@code gui.name}. */
    static final String HOST_NAME = "ScriptScreen";

    /**
     * Patch keys {@code setControl} accepts (G-003 §4 + G-036 control
     * fields + G-036B widget face): state keys land in the cache, the
     * transient keys ({@code cursor/cursorTo/shift/selection/click/cycle})
     * reach the widget only.
     */
    private static final java.util.Set<String> PATCH_KEYS = java.util.Set.of(
            "checked", "value", "text", "locked", "label", "active", "visible", "z",
            "x", "y", "width", "tooltip",
            "steps", "editable", "maxLength", "suggestion", "textColor",
            "uneditableColor", "cursor", "cursorTo", "shift", "selection",
            "click", "cycle");

    private static final AtomicLong LAST_LOG = new AtomicLong();

    /** Shared budget guard for every session callback (report-only). */
    private static final SyncWatchdog WATCHDOG = new SyncWatchdog(violation ->
            logThrottled("screen callback over budget: " + violation.eventName()
                    + " took " + violation.elapsedMillis() + "ms"));

    private final ScreenPort port;
    private final ScreenSpec spec;
    /** Screen-level callback slots: {@link Predicate} from {@link #on}, {@link Consumer} from the spec. */
    private final Map<String, Object> callbacks = new LinkedHashMap<>();
    private final Map<String, Consumer<Object[]>> clickCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Consumer<Object[]>> changeCallbacks = new ConcurrentHashMap<>();
    /** Per-control text filters installed through the EditBox handle (G-036). */
    private final Map<String, java.util.function.Function<String, Object>> textFilters =
            new ConcurrentHashMap<>();
    /** Per-control tooltip lists held by the widget face (G-036B). */
    private final Map<String, List<String>> tooltips = new ConcurrentHashMap<>();
    /** Per-control cycling-button list toggles (G-036B, builder-supplied). */
    private final Map<String, java.util.function.BooleanSupplier> alternateToggles =
            new ConcurrentHashMap<>();
    /** The mutable control order (spec mirror; the port owns its own neutral copy). */
    private final List<ControlSpec> controls = new ArrayList<>();
    private final Map<String, ControlState> cache = new ConcurrentHashMap<>();

    private volatile long session = -1L;
    private volatile boolean closed;
    private volatile boolean closing;
    private volatile boolean cacheDirty;

    private BuiltScreen(ScreenPort port, ScreenSpec spec) {
        this.port = port;
        this.spec = spec;
        this.controls.addAll(spec.controls());
        this.callbacks.put("mouseDown", spec.onMouseDown());
        this.callbacks.put("mouseDrag", spec.onMouseDrag());
        this.callbacks.put("mouseUp", spec.onMouseUp());
        this.callbacks.put("keyPress", spec.onKeyPress());
        this.callbacks.put("charTyped", spec.onCharTyped());
        this.callbacks.put("render", spec.onRender());
        this.callbacks.put("close", spec.onClose());
        for (ControlSpec control : controls) {
            cache.put(control.id(), control.toState());
            if (control.onClick() != null) {
                clickCallbacks.put(control.id(), control.onClick());
            }
            if (control.onChange() != null) {
                changeCallbacks.put(control.id(), control.onChange());
            }
        }
    }

    /**
     * Parses the script spec, opens the session through the port, and
     * registers the owner-unload disposer (best effort). The facade
     * normalizes every open failure into a synchronous, catchable throw.
     *
     * @throws IllegalArgumentException on an invalid spec
     * @throws IllegalStateException when the main-thread open fails
     */
    @ApiInternal
    public static BuiltScreen open(ScreenPort port, Map<String, Object> raw) {
        ScreenSpec spec = ScreenSpec.parse(raw);
        BuiltScreen screen = new BuiltScreen(port, spec);
        Long id;
        try {
            id = port.open(spec.toNeutral(), screen.new Sink());
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("screen could not be opened: " + t, t);
        }
        if (id == null) {
            throw new IllegalStateException(
                    "screen could not be opened (main thread busy or unavailable)");
        }
        screen.session = id;
        LoadedModule owner = LoadTracker.current();
        if (owner != null) {
            owner.onRelease(screen::unloadClose);
        }
        return screen;
    }

    /** The screen title (plain text).
     * @zh 屏幕标题（纯文本）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public String getTitle() {
        return spec.title() != null ? spec.title() : "";
    }

    /** The host class name ({@code ScriptScreen}).
     * @zh 宿主类名（{@code ScriptScreen}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public String getName() {
        return HOST_NAME;
    }

    /** Whether the session reached its terminal state.
     * @zh 会话是否已到终态。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public boolean isClosed() {
        return closed;
    }

    /** Whether Shift is currently held.
     * @zh Shift 是否按下。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public boolean getShiftDown() {
        return port.shiftDown();
    }

    /** Whether Ctrl is currently held.
     * @zh Ctrl 是否按下。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public boolean getControlDown() {
        return port.controlDown();
    }

    /** Whether Alt is currently held.
     * @zh Alt 是否按下。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public boolean getAltDown() {
        return port.altDown();
    }

    /** Where closing returns to: {@code current} or {@code game}.
     * @zh 关闭后回到哪里：{@code current} 或 {@code game}。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public String getReturnTo() {
        return spec.returnTo();
    }

    /** Live control snapshots in (z, declaration) order; empty when closed.
     * @zh 按（z, 声明序）排列的控件活快照；关闭后为空。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public List<ControlState> getControls() {
        if (closed) {
            return List.of();
        }
        refreshIfDirty();
        List<ControlState> out = new ArrayList<>(controls.size());
        for (ControlSpec control : controls) {
            ControlState state = cache.get(control.id());
            if (state != null) {
                out.add(state);
            }
        }
        return out;
    }

    /** One control snapshot, or undefined for a closed session/unknown id.
     * @zh 单个控件快照；已关闭会话或未知 id 为 undefined。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ControlState control(@ApiType("string") String id) {
        if (closed || id == null) {
            return null;
        }
        refreshIfDirty();
        return cache.get(id);
    }

    /** The live button/image-button handle for one control (G-036).
     * @zh 单个 button/imageButton 控件的活句柄（G-036）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Button button(@ApiType("string") String id) {
        ControlSpec spec = specOf(id, "button", "imageButton");
        if (spec == null) {
            return null;
        }
        return "imageButton".equals(spec.type())
                ? new ImageButton(this, id) : new Button(this, id);
    }

    /** The live checkbox handle for one control (G-036).
     * @zh 单个 checkbox 控件的活句柄（G-036）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Checkbox checkbox(@ApiType("string") String id) {
        return specOf(id, "checkbox") == null ? null : new Checkbox(this, id);
    }

    /** The live slider handle for one control (G-036).
     * @zh 单个 slider 控件的活句柄（G-036）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Slider slider(@ApiType("string") String id) {
        return specOf(id, "slider") == null ? null : new Slider(this, id);
    }

    /** The live edit-box handle for one control (G-036).
     * @zh 单个 editBox 控件的活句柄（G-036）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox editBox(@ApiType("string") String id) {
        return specOf(id, "editBox") == null ? null : new EditBox(this, id);
    }

    /** The live cycling-button handle for one control (G-036B).
     * @zh 单个 cycleButton 控件的活句柄（G-036B）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CycleButton cycleButton(@ApiType("string") String id) {
        return specOf(id, "cycleButton") == null ? null : new CycleButton(this, id);
    }

    /** The live lock-button handle for one control (G-036B).
     * @zh 单个 lockButton 控件的活句柄（G-036B）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public LockButton lockButton(@ApiType("string") String id) {
        return specOf(id, "lockButton") == null ? null : new LockButton(this, id);
    }

    /** Resolves one control for a typed handle: null when closed/unknown,
     * throws when the id names a control of another type. */
    ControlSpec specOf(String id, String... types) {
        if (closed || id == null) {
            return null;
        }
        for (ControlSpec control : controls) {
            if (control.id().equals(id)) {
                for (String type : types) {
                    if (type.equals(control.type())) {
                        return control;
                    }
                }
                throw new IllegalArgumentException("control '" + id + "' is a "
                        + control.type() + ", not " + java.util.Arrays.toString(types));
            }
        }
        return null;
    }

    /**
     * Appends one control (rebuilt immediately, same session). Accepts a
     * plain spec object literal or a builder's {@code build()} product:
     * the engine hands both over as maps (records snapshot to their full
     * component set, absent optionals as null — {@link ControlSpec#parse}
     * reads those as unset), and the {@code Consumer} callback fields
     * survive that snapshot, so builder callbacks reach the session.
     *
     * @zh 追加一个控件（同会话立即重建）。既接受声明式对象字面量，也接受构建器 {@code build()} 产物：引擎两者都以 map 交出（record 快照为全组件集、缺席可选字段为 null——{@link ControlSpec#parse} 将其读作未设置），{@code Consumer} 回调字段在快照中存留，构建器回调得以进入会话。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BuiltScreen add(@ApiType("ControlSpec") Object control) {
        ensureOpen();
        ControlSpec parsed;
        if (control instanceof ControlSpec spec) {
            parsed = spec;
        } else if (control instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) map;
            parsed = ControlSpec.parse(raw);
        } else {
            throw new IllegalArgumentException(
                    "add expects a control object, got: "
                            + (control == null ? "null"
                                    : control.getClass().getName()));
        }
        for (ControlSpec existing : controls) {
            if (existing.id().equals(parsed.id())) {
                throw new IllegalArgumentException(
                        "duplicate control id: " + parsed.id());
            }
        }
        controls.add(parsed);
        cache.put(parsed.id(), parsed.toState());
        if (parsed.onClick() != null) {
            clickCallbacks.put(parsed.id(), parsed.onClick());
        }
        if (parsed.onChange() != null) {
            changeCallbacks.put(parsed.id(), parsed.onChange());
        }
        if (parsed.alternateToggle() != null) {
            alternateToggles.put(parsed.id(), parsed.alternateToggle());
        } else {
            alternateToggles.remove(parsed.id());
        }
        port.addControl(session, parsed.toNeutral());
        return this;
    }

    /** Removes one control by id (rebuilt immediately).
     * @zh 按 id 移除一个控件（立即重建）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BuiltScreen remove(@ApiType("string") String id) {
        ensureOpen();
        boolean present = controls.stream().anyMatch(control -> control.id().equals(id));
        if (!present) {
            throw new IllegalArgumentException("unknown control id: " + id);
        }
        controls.removeIf(control -> control.id().equals(id));
        cache.remove(id);
        clickCallbacks.remove(id);
        changeCallbacks.remove(id);
        textFilters.remove(id);
        tooltips.remove(id);
        alternateToggles.remove(id);
        port.removeControl(session, id);
        return this;
    }

    /**
     * Applies a whitelisted state patch ({@code checked/value/text/locked/
     * label/active/visible/z}) to one live control.
     *
     * @zh 对一个活控件应用白名单状态补丁（{@code checked/value/text/locked/label/active/visible/z}）。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BuiltScreen setControl(@ApiType("string") String id,
                                  @ApiType("Record<string, any>") Map<String, Object> patch) {
        ensureOpen();
        boolean present = controls.stream().anyMatch(control -> control.id().equals(id));
        if (!present) {
            throw new IllegalArgumentException("unknown control id: " + id);
        }
        if (patch == null || patch.isEmpty()) {
            throw new IllegalArgumentException("setControl requires a patch object");
        }
        for (String key : patch.keySet()) {
            if (!PATCH_KEYS.contains(key)) {
                throw new IllegalArgumentException(
                        "setControl field not allowed: " + key);
            }
        }
        port.writeControl(session, id, patch);
        cache.computeIfPresent(id, (key, state) -> applyPatch(state, patch));
        return this;
    }

    /**
     * Swaps one screen-level callback at runtime: {@code mouseDown},
     * {@code mouseDrag}, {@code mouseUp}, {@code keyPress},
     * {@code charTyped}, {@code render}, {@code close}; {@code null} clears.
     * Input callbacks answer {@code true} = consumed.
     *
     * @zh 运行期换装一个屏级回调：{@code mouseDown/mouseDrag/mouseUp/keyPress/charTyped/render/close}；{@code null} 清除。输入回调返回 {@code true} 即消费。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BuiltScreen on(@ApiType("string") String name,
                          @ApiType("((e: any) => boolean) | null") Predicate<Object> callback) {
        ensureOpen();
        if (!ScreenSpec.CALLBACK_NAMES.contains(name)) {
            throw new IllegalArgumentException("unknown screen callback: " + name);
        }
        callbacks.put(name, callback);
        return this;
    }

    /**
     * Closes the screen (close intent: {@code close} callback first, then
     * return to the captured parent or the game per {@code returnTo}).
     * Throws once the session is closed.
     *
     * <p>The close callback fires on the calling thread before the port's
     * main-thread hop: that hop must never need the context loop back
     * (a loop blocked waiting on the main thread would deadlock the
     * bridge). The port hop then only restores the parent screen, and its
     * terminal notification is an idempotent no-op.</p>
     *
     * @zh 关闭屏幕（关闭意图：先 {@code close} 回调，再按 {@code returnTo} 回捕获的父屏或游戏）。已关闭再调即 throw。回调先在调用线程触发，主线程跳只负责回父屏。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public void close() {
        ensureOpen();
        sessionClosed();
        port.close(session);
    }

    /**
     * Adopts live widget state and rebuilds the control tree without
     * ending the session (callbacks and owner stay attached).
     *
     * @zh 收纳活控件状态并重建控件树，会话不结束（回调与 owner 保持挂接）。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BuiltScreen reload() {
        ensureOpen();
        port.reload(session);
        cacheDirty = true;
        return this;
    }

    // ------------------------------------------------------------------
    // SessionSink driving (internal; reached only through the port)
    // ------------------------------------------------------------------

    private void sessionRender(DrawContext2d ctx, double mouseX, double mouseY,
                               float partialTick) {
        if (closed || closing || session < 0) {
            return;
        }
        Object callback = callbacks.get("render");
        if (callback == null) {
            return;
        }
        ScreenCanvas canvas = new ScreenCanvas(ctx);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ctx", canvas);
        payload.put("mouse", Map.of("x", mouseX, "y", mouseY));
        payload.put("partialTick", (double) partialTick);
        try {
            dispatch("render", () -> invoke(callback, payload));
        } finally {
            canvas.revoke();
        }
    }

    private boolean sessionInput(String name, Object payload) {
        if (closed || closing || session < 0) {
            return false;
        }
        Object callback = callbacks.get(name);
        if (callback == null) {
            return false;
        }
        boolean[] consumed = {false};
        dispatch(name, () -> {
            try {
                consumed[0] = testInput(callback, payload);
            } catch (Throwable t) {
                logThrottled("screen." + name + " callback failed: " + t);
            }
        });
        return consumed[0];
    }

    /** Runs an input callback: {@code true} from a Predicate reads as consumed. */
    private static boolean testInput(Object callback, Object payload) {
        if (callback instanceof Predicate<?> raw) {
            @SuppressWarnings("unchecked")
            Predicate<Object> predicate = (Predicate<Object>) raw;
            return Boolean.TRUE.equals(predicate.test(payload));
        }
        invoke(callback, payload);
        return false;
    }

    private void sessionControlEvent(String id, String event, Map<String, Object> payload) {
        if (closed || closing || session < 0) {
            return;
        }
        Consumer<Object[]> callback = "click".equals(event)
                ? clickCallbacks.get(id) : changeCallbacks.get(id);
        if (callback == null) {
            return;
        }
        dispatch("control." + id, () -> {
            try {
                callback.accept(new Object[]{payload});
            } catch (Throwable t) {
                logThrottled("screen control " + event + " callback failed: " + t);
            }
        });
    }

    private void sessionStateChanged(String id, ScreenPort.ControlState state) {
        if (!closed && id != null && state != null) {
            cache.put(id, ControlState.from(state));
        }
    }

    /**
     * Runs the registered text filter for one edit box (G-036): no
     * predicate answers accept; a predicate rejects only on an explicit
     * {@code false} return (fail-open — a callback that throws or returns
     * nothing lets the text through, matching the "swallowed callback"
     * convention).
     */
    private boolean sessionFilterText(String id, String candidate) {
        if (closed || closing || session < 0) {
            return true;
        }
        java.util.function.Function<String, Object> filter = textFilters.get(id);
        if (filter == null) {
            return true;
        }
        boolean[] accept = {true};
        dispatch("filter." + id, () -> {
            try {
                accept[0] = !Boolean.FALSE.equals(filter.apply(candidate));
            } catch (Throwable t) {
                logThrottled("screen text filter failed: " + t);
            }
        });
        return accept[0];
    }

    /**
     * Runs the registered cycling-button list toggle for one control
     * (G-036B): no supplier answers {@code false} (the default list), a
     * supplier that throws fails to the default list as well — the
     * adapter only reaches this channel when the control was built with
     * a script {@code alternateToggle}.
     */
    private boolean sessionAlternateToggle(String id) {
        if (closed || closing || session < 0) {
            return false;
        }
        java.util.function.BooleanSupplier toggle = alternateToggles.get(id);
        if (toggle == null) {
            return false;
        }
        if (ScreenPort.isDispatchActive()) {
            // A widget rebuilt from inside a guest callback would re-enter
            // the loop that is already parked on this thread (deadlock);
            // answer the default list for that construction-time pick.
            return false;
        }
        boolean[] alternate = {false};
        dispatch("altToggle." + id, () -> {
            try {
                alternate[0] = toggle.getAsBoolean();
            } catch (Throwable t) {
                logThrottled("screen alternate toggle failed: " + t);
            }
        });
        return alternate[0];
    }

    /** Terminal transition: fires the close callback once, then invalidates. */
    private void sessionClosed() {
        if (closed || closing) {
            return;
        }
        closing = true;
        try {
            Object callback = callbacks.get("close");
            if (callback != null && session >= 0) {
                dispatch("close", () -> {
                    try {
                        invoke(callback, null);
                    } catch (Throwable t) {
                        logThrottled("screen.close callback failed: " + t);
                    }
                });
            }
        } finally {
            closed = true;
            closing = false;
            cache.clear();
            textFilters.clear();
            tooltips.clear();
            alternateToggles.clear();
        }
    }

    /** Owner unload path: close intent under the dispatch gate (inline port hop). */
    private void unloadClose() {
        if (closed || session < 0) {
            return;
        }
        dispatch("unload", () -> {
            try {
                port.close(session);
            } catch (Throwable t) {
                logThrottled("screen unload close failed: " + t);
            }
        });
    }

    /** Runs one guest invocation under the dispatch gate and the watchdog. */
    private void dispatch(String name, Runnable body) {
        ScreenPort.beginDispatch();
        try {
            WATCHDOG.guard("screen." + name, body);
        } finally {
            ScreenPort.endDispatch();
        }
    }

    private static void invoke(Object callback, Object payload) {
        try {
            if (callback instanceof Predicate<?> raw) {
                @SuppressWarnings("unchecked")
                Predicate<Object> predicate = (Predicate<Object>) raw;
                predicate.test(payload);
            } else if (callback instanceof Consumer<?> raw) {
                @SuppressWarnings("unchecked")
                Consumer<Object[]> consumer = (Consumer<Object[]>) raw;
                consumer.accept(payload != null ? new Object[]{payload} : new Object[0]);
            }
        } catch (Throwable t) {
            logThrottled("screen callback failed: " + t);
        }
    }

    private void ensureOpen() {
        if (closed || closing) {
            throw new IllegalStateException("screen is closed");
        }
        if (session < 0) {
            throw new IllegalStateException("screen is not open");
        }
    }

    // ------------------------------------------------------------------
    // G-036 typed-handle support (package-private: handles share the package)
    // ------------------------------------------------------------------

    /** Installs the edit-box text filter for one control (handle path). */
    void putTextFilter(String id, java.util.function.Function<String, Object> filter) {
        ensureOpen();
        textFilters.put(id, filter);
    }

    /** Clears the edit-box text filter for one control (handle path). */
    void clearTextFilter(String id) {
        textFilters.remove(id);
    }

    /** Replaces the tooltip list of one control (handle path, G-036B). */
    void replaceTooltips(String id, List<String> lines) {
        ensureOpen();
        tooltips.put(id, List.copyOf(lines));
    }

    /** The tooltip list of one control; empty when none/closed (G-036B). */
    List<String> tooltipsOf(String id) {
        List<String> lines = tooltips.get(id);
        return lines != null ? lines : List.of();
    }

    private void refreshIfDirty() {
        if (!cacheDirty) {
            return;
        }
        cacheDirty = false;
        for (ControlSpec control : controls) {
            port.readControl(session, control.id())
                    .ifPresent(state -> cache.put(control.id(), ControlState.from(state)));
        }
    }

    private static ControlState applyPatch(ControlState state, Map<String, Object> patch) {
        return new ControlState(
                state.id(),
                state.type(),
                patchInteger(patch, "x", state.x()),
                patchInteger(patch, "y", state.y()),
                patchInteger(patch, "width", state.width()),
                state.height(),
                patchInteger(patch, "z", state.z()),
                patch.containsKey("label") && patch.get("label") instanceof String label
                        ? label : state.label(),
                patch.containsKey("active") && patch.get("active") instanceof Boolean active
                        ? active : state.active(),
                patch.containsKey("visible") && patch.get("visible") instanceof Boolean visible
                        ? visible : state.visible(),
                patch.containsKey("checked") && patch.get("checked") instanceof Boolean checked
                        ? checked : state.checked(),
                patch.containsKey("value") && patch.get("value") instanceof Number value
                        ? value.doubleValue() : state.value(),
                patch.containsKey("text") && patch.get("text") instanceof String text
                        ? text : state.text(),
                patch.containsKey("locked") && patch.get("locked") instanceof Boolean locked
                        ? locked : state.locked(),
                patch.get("steps") instanceof Number steps
                        ? steps.intValue() : state.steps(),
                patch.containsKey("editable") && patch.get("editable") instanceof Boolean editable
                        ? editable : state.editable(),
                patch.get("maxLength") instanceof Number length
                        ? length.intValue() : state.maxLength(),
                state.selectedText());
    }

    private static int patchInteger(Map<String, Object> patch, String key, int fallback) {
        return patch.get(key) instanceof Number number ? number.intValue() : fallback;
    }

    /** Throttled diagnostics (3 s floor) so a hot callback cannot spam logs. */
    static void logThrottled(String message) {
        long now = System.currentTimeMillis();
        long last = LAST_LOG.get();
        if (now - last >= 3000 && LAST_LOG.compareAndSet(last, now)) {
            System.err.println("[NeoMacros] " + message);
        }
    }

    /** Mouse payload for the screen-level input callbacks. */
    static Map<String, Object> mousePayload(ScreenPort.MouseInput input, boolean withDelta) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pos", new Pos2D(input.x(), input.y()));
        payload.put("button", input.button());
        payload.put("modifiers", input.modifiers());
        if (input.doubleClick() != null) {
            payload.put("doubleClick", input.doubleClick());
        }
        if (withDelta) {
            payload.put("delta", Map.of("x", input.dx(), "y", input.dy()));
        }
        return payload;
    }

    /** The port-side session sink (kept private so it never reaches docs). */
    private final class Sink implements ScreenPort.SessionSink {

        @Override
        public void render(DrawContext2d ctx, double mouseX, double mouseY,
                           float partialTick) {
            sessionRender(ctx, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseDown(ScreenPort.MouseInput e) {
            return sessionInput("mouseDown", mousePayload(e, false));
        }

        @Override
        public boolean mouseDrag(ScreenPort.MouseInput e) {
            return sessionInput("mouseDrag", mousePayload(e, true));
        }

        @Override
        public boolean mouseUp(ScreenPort.MouseInput e) {
            return sessionInput("mouseUp", mousePayload(e, false));
        }

        @Override
        public boolean keyPress(ScreenPort.KeyInput e) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("key", e.key());
            payload.put("code", e.code());
            payload.put("modifiers", e.modifiers());
            return sessionInput("keyPress", payload);
        }

        @Override
        public boolean charTyped(ScreenPort.CharInput e) {
            return sessionInput("charTyped", Map.of("char", e.character()));
        }

        @Override
        public void controlEvent(String controlId, String event,
                                 Map<String, Object> payload) {
            sessionControlEvent(controlId, event, payload);
        }

        @Override
        public void stateChanged(String controlId, ScreenPort.ControlState state) {
            sessionStateChanged(controlId, state);
        }

        @Override
        public boolean filterText(String controlId, String candidate) {
            return sessionFilterText(controlId, candidate);
        }

        @Override
        public boolean alternateToggle(String controlId) {
            return sessionAlternateToggle(controlId);
        }

        @Override
        public void closed() {
            sessionClosed();
        }
    }
}
