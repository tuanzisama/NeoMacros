package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.core.runtime.TimerHandle;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;

/**
 * The {@code neo.thread} module: thread introspection, time-based and
 * main-thread asynchronous primitives, and timers, each bound to the calling
 * script's context event loop. Every scheduling function returns a promise or
 * a cancellable timer handle.
 *
 * @zh {@code neo.thread}：线程自省、基于时间与主线程的异步原语与定时器，各自绑定调用脚本的上下文事件循环；调度函数返回 Promise 或可取消的定时器句柄。
 */
@ApiModule(name = "thread", category = "核心")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class ThreadApi {

    /**
     * Whether the calling code is currently running on the game main thread.
     *
     * @zh 当前代码是否在游戏主线程上执行。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isMain() {
        return primitives().isMain();
    }

    /**
     * Resolves after the given delay in milliseconds; the delay is real time,
     * independent of game ticks.
     *
     * @zh 毫秒级实时延时。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<any>")
    public CompletableFuture<Object> runOnMain(@ApiType("() => any") Callable<Object> task) {
        return primitives().runOnMain(task);
    }

    /**
     * Runs the function once on the script loop after {@code millis} of real
     * time; the returned handle cancels the pending run.
     *
     * @zh 毫秒后在脚本事件循环上执行一次函数；返回句柄可取消。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TimerHandle after(double millis, Runnable task) {
        return primitives().after((long) millis, task);
    }

    /**
     * Runs the function once on the script loop after {@code count} client
     * tick boundaries; the returned handle cancels the pending run.
     *
     * @zh 指定数量的客户端 tick 后在脚本事件循环上执行一次函数；返回句柄可取消。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TimerHandle afterTick(int count, Runnable task) {
        return primitives().afterTicks(count, task);
    }

    /**
     * Runs the function on the script loop every {@code millis} of real time
     * until the returned handle is cancelled.
     *
     * @zh 每隔指定毫秒在脚本事件循环上执行函数，直到句柄取消。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TimerHandle every(double millis, Runnable task) {
        return primitives().every((long) millis, task);
    }

    /**
     * Runs the function on the script loop every {@code count} client tick
     * boundaries until the returned handle is cancelled.
     *
     * @zh 每隔指定数量的客户端 tick 在脚本事件循环上执行函数，直到句柄取消。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TimerHandle everyTick(int count, Runnable task) {
        return primitives().everyTicks(count, task);
    }

    private ink.tuanzi.neomacros.core.runtime.AsyncPrimitives primitives() {
        LoadedModule owner = LoadTracker.current();
        if (owner == null) {
            throw new IllegalStateException(
                    "thread primitives require a script context (they bind to the context's event loop)");
        }
        return owner.context().async();
    }
}
