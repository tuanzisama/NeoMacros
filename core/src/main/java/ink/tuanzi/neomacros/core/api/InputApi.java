package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.core.api.ScriptHandle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The {@code neo.input} module: keybind registration (surfaced in the vanilla
 * controls menu), raw key press listening, mouse wheel events, and key
 * injection ({@code press/release/hold/set}).
 *
 * @zh {@code neo.input}：按键绑定注册（显示在原版控制设置中）、原始按键监听、鼠标滚轮事件与按键注入（{@code press/release/hold/set}）。
 */
@ApiModule(name = "input", category = "输入")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class InputApi {

    private final InputBridge bridge;
    private final ScheduledExecutorService delayer;

    /** Creates the API over the input bridge (bootstrap-provided). */
    public InputApi(InputBridge bridge) {
        this(bridge, defaultDelayer());
    }

    /** Creates the API with an explicit delay scheduler (for hold). */
    public InputApi(InputBridge bridge, ScheduledExecutorService delayer) {
        this.bridge = bridge;
        this.delayer = delayer;
    }

    private static ScheduledExecutorService defaultDelayer() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, run -> {
            Thread thread = new Thread(run, "neomacros-input-delay");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    /**
     * Registers a persistent keybind; the sink fires on every press (repeats
     * while held, per vanilla click semantics). The binding appears in the
     * vanilla controls menu under the NeoMacros category.
     *
     * @param keyName vanilla key name, e.g. {@code key.keyboard.k}
     * @param sink press callback
     * @return a subscription; cancel unbinds
     * @zh 注册一个持久按键绑定；按键回调在每次按下时触发。绑定会出现在原版控制设置中。
     * @zh-param keyName 原版按键名，如 {@code key.keyboard.k}
     * @zh-return 订阅句柄，cancel 解绑
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Subscription registerKeybind(String keyName, Runnable sink) {
        Object handle = bridge.registerKeybind(keyName, count -> sink.run());
        return new Subscription(() -> bridge.unregister(handle));
    }

    /**
     * Registers a one-shot press listener for a raw key.
     *
     * @zh 监听单个按键的按下沿。
     * @zh-return 订阅句柄，cancel 注销
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Subscription registerKeyPress(String keyName, Runnable sink) {
        Object handle = bridge.registerKeyPress(keyName, sink);
        return new Subscription(() -> bridge.unregister(handle));
    }

    /**
     * Subscribes to mouse wheel scroll; the value is the scroll offset
     * (positive = wheel up).
     *
     * @zh 订阅鼠标滚轮事件；正值表示向上滚动。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<void>")
    public CompletableFuture<Void> onScroll(java.util.function.DoubleConsumer sink) {
        bridge.onScroll(sink);
        return CompletableFuture.completedFuture(null);
    }

    /** Removes a previously registered keybind or press listener.
     * @zh 注销先前注册的按键绑定或按下监听。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void unregister(Object handle) {
        if (handle instanceof Subscription subscription) {
            subscription.cancel();
            return;
        }
        bridge.unregister(handle);
    }

    /**
     * Injects one raw key press (down edge).
     *
     * @zh 注入一个原始按键的按下沿。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void press(String keyName) {
        bridge.injectKey(keyName, true);
    }

    /**
     * Injects one raw key release (up edge).
     *
     * @zh 注入一个原始按键的松开沿。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void release(String keyName) {
        bridge.injectKey(keyName, false);
    }

    /**
     * Holds a raw key down for the given duration, then releases; the
     * promise resolves after the release.
     *
     * @zh 按住一个原始按键指定毫秒后松开；Promise 在松开后落定。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<void>")
    public CompletableFuture<Void> hold(String keyName, long millis) {
        bridge.injectKey(keyName, true);
        CompletableFuture<Void> done = new CompletableFuture<>();
        delayer.schedule(() -> {
            try {
                bridge.injectKey(keyName, false);
            } finally {
                done.complete(null);
            }
        }, millis, TimeUnit.MILLISECONDS);
        return done;
    }

    /**
     * Injects one keybind state by bind name ({@code key.use} and friends),
     * as if the user pressed the bound input.
     *
     * @zh 按键位绑定名（如 key.use）注入按下/松开状态。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void set(String bindName, boolean down) {
        bridge.injectBind(bindName, down);
    }

    /**
     * The vanilla keybind rows as {@code [{name, key}]} in registration
     * order; {@code key} is the friendly label of the bound key.
     *
     * @zh 原版键位表 {@code [{name, key}]}，按注册顺序；{@code key} 为绑定键的友好名称。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public java.util.List<java.util.Map<String, Object>> getKeybinds() {
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (ink.tuanzi.neomacros.port.model.KeybindData bind : bridge.keybinds()) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("name", bind.name());
            row.put("key", bind.key());
            rows.add(row);
        }
        return rows;
    }

    /**
     * Rebinds one vanilla keybind to the key description
     * ({@code key.keyboard.w} form).
     *
     * @zh 重新绑定一个原版键位到按键描述（{@code key.keyboard.w} 形式）。
     * @zh-param bindName 键位绑定名
     * @zh-param keyName 按键描述
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void setBind(String bindName, String keyName) {
        bridge.setBind(bindName, keyName);
    }

    /**
     * The vanilla keybind names currently held down.
     *
     * @zh 当前按住的原版键位绑定名数组。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public java.util.List<String> getPressedKeys() {
        java.util.List<String> down = new java.util.ArrayList<>();
        for (ink.tuanzi.neomacros.port.model.KeybindData bind : bridge.keybinds()) {
            if (bridge.bindDown(bind.name())) {
                down.add(bind.name());
            }
        }
        return down;
    }

    /**
     * Holds the given movement binds down until the returned subscription is
     * cancelled. Recognized keys: {@code forward}, {@code backward},
     * {@code left}, {@code right}, {@code jump}, {@code sneak},
     * {@code sprint}.
     *
     * @zh 按住给定的移动键位，直到取消返回的订阅。识别键：{@code forward}/{@code backward}/{@code left}/{@code right}/{@code jump}/{@code sneak}/{@code sprint}。
     * @zh-param spec 移动键位表
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Subscription movement(java.util.Map<String, Object> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("movement spec is required");
        }
        java.util.List<String> held = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Object> entry : spec.entrySet()) {
            String bind = MOVEMENT_BINDS.get(entry.getKey());
            if (bind == null) {
                throw new IllegalArgumentException("unknown movement key: " + entry.getKey());
            }
            if (Boolean.TRUE.equals(entry.getValue())
                    || "true".equals(String.valueOf(entry.getValue()))) {
                held.add(bind);
            }
        }
        for (String bind : held) {
            bridge.injectBind(bind, true);
        }
        return new Subscription(() -> {
            for (String bind : held) {
                bridge.injectBind(bind, false);
            }
        });
    }

    /**
     * The movement bind states as {@code {forward, backward, left, right,
     * jump, sneak, sprint}}.
     *
     * @zh 移动键位状态 {@code {forward, backward, left, right, jump, sneak, sprint}}。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public java.util.Map<String, Boolean> getMovementState() {
        java.util.Map<String, Boolean> view = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, String> entry : MOVEMENT_BINDS.entrySet()) {
            view.put(entry.getKey(), bridge.bindDown(entry.getValue()));
        }
        return view;
    }

    /** The vanilla bind names behind each movement key. */
    private static final java.util.Map<String, String> MOVEMENT_BINDS = java.util.Map.of(
            "forward", "key.forward",
            "backward", "key.back",
            "left", "key.left",
            "right", "key.right",
            "jump", "key.jump",
            "sneak", "key.sneak",
            "sprint", "key.sprint");

    /** A live registration that can be cancelled. */
    public static final class Subscription implements ScriptHandle {

        private final Runnable cancel;

        Subscription(Runnable cancel) {
            this.cancel = cancel;
        }

        /** Cancels the registration (unbinds / detaches). */
        public void cancel() {
            cancel.run();
        }
    }

    /** Engine-facing input bridge implemented by the input port wiring. */
    public interface InputBridge {
        Object registerKeybind(String keyName, Consumer<Integer> sink);

        Object registerKeyPress(String keyName, Runnable sink);

        void onScroll(java.util.function.DoubleConsumer sink);

        void unregister(Object handle);

        /** Injects one raw key state. */
        void injectKey(String keyName, boolean down);

        /** Injects one keybind state by bind name. */
        void injectBind(String bindName, boolean down);

        /** Returns whether the vanilla keybind is currently held down. */
        boolean bindDown(String bindName);

        /** Returns the vanilla keybind rows in registration order. */
        java.util.List<ink.tuanzi.neomacros.port.model.KeybindData> keybinds();

        /** Rebinds one vanilla keybind to the key description. */
        void setBind(String bindName, String keyName);
    }
}
