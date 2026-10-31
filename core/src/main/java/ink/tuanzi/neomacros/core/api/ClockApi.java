package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;

/**
 * The {@code neo.clock} facade: time-based and main-thread asynchronous
 * primitives, each bound to the calling script's context event loop. Every
 * function returns a promise.
 */
@ApiModule(name = "clock", category = "核心")
public final class ClockApi {

    /**
     * Resolves after the given delay in milliseconds; the delay is real time,
     * independent of game ticks.
     *
     * @zh 毫秒级实时延时。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<void>")
    public CompletableFuture<Void> sleep(double millis) {
        return primitives().sleep((long) millis);
    }

    /**
     * Resolves after {@code count} client ticks have passed.
     *
     * @zh 等待指定数量的客户端 tick。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<void>")
    public CompletableFuture<Void> ticks(int count) {
        return primitives().ticks(count);
    }

    /**
     * Resolves at the end of the current client tick (or the next one when
     * called between ticks).
     *
     * @zh 等待当前 tick 结束。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<void>")
    public CompletableFuture<Void> nextTick() {
        return primitives().nextTick();
    }

    /**
     * Runs the function on the game main thread; the promise settles with its
     * return value. Calling this from the main thread executes inline.
     *
     * @zh 将函数投递到游戏主线程执行，Promise 以返回值落定。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<object>")
    public CompletableFuture<Object> runOnMain(Callable<Object> task) {
        return primitives().runOnMain(task);
    }

    private ink.tuanzi.neomacros.core.runtime.AsyncPrimitives primitives() {
        LoadedModule owner = LoadTracker.current();
        if (owner == null) {
            throw new IllegalStateException(
                    "clock primitives require a script context (they bind to the context's event loop)");
        }
        return owner.context().async();
    }
}
