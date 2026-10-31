package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The {@code neo.input} facade: keybind registration (surfaced in the vanilla
 * controls menu), raw key press listening, and mouse wheel events.
 */
@ApiModule(name = "input", category = "输入")
public final class InputApi {

    private final InputBridge bridge;

    /** Creates the facade over the input bridge (bootstrap-provided). */
    public InputApi(InputBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * Registers a persistent keybind; the sink fires on every press (repeats
     * while held, per vanilla click semantics). The binding appears in the
     * vanilla controls menu under the NeoMacros category.
     *
     * @param keyName vanilla key name, e.g. {@code key.keyboard.k}
     * @param sink press callback
     * @return an opaque registration handle (unregister via {@link #unregisterKeybind})
     * @zh 注册一个持久按键绑定；按键回调在每次按下时触发。绑定会出现在原版控制设置中。
     * @zh-param keyName 原版按键名，如 {@code key.keyboard.k}
     */
    @ApiSince("0.1.0")
    public Object registerKeybind(String keyName, Runnable sink) {
        return bridge.registerKeybind(keyName, count -> sink.run());
    }

    /**
     * Registers a one-shot press listener for a raw key.
     *
     * @zh 监听单个按键的按下沿。
     */
    @ApiSince("0.1.0")
    public Object registerKeyPress(String keyName, Runnable sink) {
        return bridge.registerKeyPress(keyName, sink);
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
        bridge.unregister(handle);
    }

    /** Engine-facing input bridge implemented by the input port wiring. */
    public interface InputBridge {
        Object registerKeybind(String keyName, Consumer<Integer> sink);

        Object registerKeyPress(String keyName, Runnable sink);

        void onScroll(java.util.function.DoubleConsumer sink);

        void unregister(Object handle);
    }
}
