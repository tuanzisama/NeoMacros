package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.core.api.ScriptHandle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The {@code neo.input} facade: keybind registration (surfaced in the vanilla
 * controls menu), raw key press listening, mouse wheel events, and key
 * injection ({@code press/release/hold/set}).
 */
@ApiModule(name = "input", category = "输入")
public final class InputApi {

    private final InputBridge bridge;
    private final ScheduledExecutorService delayer;

    /** Creates the facade over the input bridge (bootstrap-provided). */
    public InputApi(InputBridge bridge) {
        this(bridge, defaultDelayer());
    }

    /** Creates the facade with an explicit delay scheduler (for hold). */
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
    @ApiType("Promise<void>")
    public CompletableFuture<Void> onScroll(java.util.function.DoubleConsumer sink) {
        bridge.onScroll(sink);
        return CompletableFuture.completedFuture(null);
    }

    /** Removes a previously registered keybind or press listener. */
    @ApiSince("0.1.0")
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
    public void press(String keyName) {
        bridge.injectKey(keyName, true);
    }

    /**
     * Injects one raw key release (up edge).
     *
     * @zh 注入一个原始按键的松开沿。
     */
    @ApiSince("0.1.0")
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
    public void set(String bindName, boolean down) {
        bridge.injectBind(bindName, down);
    }

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
    }
}
