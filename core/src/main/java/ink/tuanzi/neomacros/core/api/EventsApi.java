package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The {@code neo.events} module: EventEmitter-style subscription over the
 * event bus. Subscriptions created while a module is loading are attributed
 * to that module and auto-unsubscribed when the module unloads.
 *
 * @zh {@code neo.events}：事件总线上的 EventEmitter 风格订阅。模块装载期创建的订阅归属该模块，模块卸载时自动退订。
 */
@ApiModule(name = "events", category = "核心")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class EventsApi {

    /**
     * Subscribes asynchronously; the handler runs on the script context's
     * event loop. Wildcard patterns ({@code domain:*}, {@code *}) are allowed.
     *
     * @return the subscription handle passed back to {@link #off(Object)}
     * @zh 异步订阅事件；处理器在脚本上下文的事件循环线程上执行。
     * @zh-param name 事件名，支持 {@code domain:*} 与 {@code *} 通配
     * @zh-param handler 事件载荷处理器
     * @zh-return 传回 {@link #off} 的订阅句柄
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public ink.tuanzi.neomacros.core.event.Subscription on(String name, Consumer<Object> handler) {
        LoadedModule owner = LoadTracker.current();
        var subscription = owner != null
                ? ScriptApiEnvironment.bus().on(name, owner.context().loop(), handler)
                : ScriptApiEnvironment.bus().on(name, handler);
        if (owner != null) {
            owner.track(subscription);
        }
        return subscription;
    }

    /**
     * Subscribes for exactly one firing; the subscription auto-unsubscribes.
     *
     * @return the subscription handle
     * @zh 订阅一次触发后自动注销的事件。
     * @zh-return 订阅句柄
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public ink.tuanzi.neomacros.core.event.Subscription once(String name, Consumer<Object> handler) {
        LoadedModule owner = LoadTracker.current();
        var subscription = owner != null
                ? ScriptApiEnvironment.bus().once(name, handler)
                : ScriptApiEnvironment.bus().once(name, handler);
        if (owner != null) {
            owner.track(subscription);
        }
        return subscription;
    }

    /**
     * Registers one custom event name owned by scripts. Names must be
     * namespaced ({@code domain:rest}) and cannot shadow a built-in domain;
     * custom events are non-cancellable and ride the async channel.
     *
     * @zh 注册脚本自定义事件；名称必须带命名空间，且不能占用内置域。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void register(String name) {
        ScriptApiEnvironment.catalog().registerCustom(name);
    }

    /**
     * Emits one custom event; the payload is a plain object (or undefined)
     * and delivery is async. The name must be registered first; payload keys
     * must not use the BaseEvent reserved words
     * ({@code type}, {@code timestamp}, {@code cancellable}, {@code direction}).
     *
     * @zh 触发一个自定义事件；载荷须为普通对象（或 undefined），走异步通道。名称须先注册；载荷键不得占用 BaseEvent 保留字（{@code type}、{@code timestamp}、{@code cancellable}、{@code direction}）。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void emit(String name, @ApiType("Record<string, JsonValue>") Object payload) {
        if (ScriptApiEnvironment.catalog().descriptor(name).isEmpty()) {
            throw new IllegalArgumentException(
                    "unknown event '" + name + "': register it with events.register first");
        }
        if (payload != null && !(payload instanceof java.util.Map)) {
            throw new IllegalArgumentException(
                    "custom event payload must be a plain object or undefined: " + name);
        }
        if (payload instanceof java.util.Map<?, ?> map) {
            for (Object key : map.keySet()) {
                if (ink.tuanzi.neomacros.core.event.EventDescriptor.RESERVED_PAYLOAD_KEYS
                        .contains(String.valueOf(key))) {
                    throw new IllegalArgumentException("payload key '" + key
                            + "' is reserved by BaseEvent (event " + name + ")");
                }
            }
        }
        ScriptApiEnvironment.bus().publish(name, payload);
    }

    /**
     * Unsubscribes by the handle returned from {@link #on} / {@link #once},
     * or by event name when a string is given.
     *
     * @zh 按句柄或事件名注销订阅。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void off(@ApiType("Subscription | string") Object handleOrName) {
        if (handleOrName instanceof String name) {
            ScriptApiEnvironment.bus().off(name);
        } else if (handleOrName instanceof ink.tuanzi.neomacros.core.event.Subscription subscription) {
            ScriptApiEnvironment.bus().off(subscription);
        }
    }

    /**
     * Subscribes synchronously on the dispatch thread; only cancellable
     * events accept sync subscriptions. The callback receives the payload in
     * writable form: rewrite whitelisted fields directly ({@code e.text = ...}),
     * cancel with {@code e.cancel()} or the writable {@code e.cancelled},
     * and return nothing — the engine commits the writes back to the
     * publisher. Later priorities observe and override earlier writes;
     * MONITOR subscriptions are observation-only.
     * Sync callbacks must stay short; they block the dispatch thread.
     *
     * @param options optional {@code { priority: neo.EventPriority.*, ignoreCancelled: boolean }}
     * @zh 在分发线程上同步订阅；仅可取消事件可用。回调收到的载荷可写：直接改写白名单字段、用 {@code e.cancel()} 或可写的 {@code e.cancelled} 取消，无需返回值——引擎把修改提交回发布方。后执行的优先级能看到并覆盖先前修改；MONITOR 仅作观测。同步回调必须短小，会阻塞分发线程。
     * @zh-param name 事件名（仅可取消事件）
     * @zh-param handler 处理回调：通过事件对象提交修改与取消；须短小
     * @zh-param options 可选 {@code { priority: neo.EventPriority.*, ignoreCancelled: 布尔 }}
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void onSync(String name, java.util.function.Function<Object, Object> handler,
                       @ApiType("SyncOptions") Object options) {
        var priority = ink.tuanzi.neomacros.core.event.EventPriority.NORMAL;
        boolean ignoreCancelled = false;
        if (options instanceof java.util.Map<?, ?> map) {
            if (map.get("priority") != null) {
                if (!(map.get("priority") instanceof ink.tuanzi.neomacros.core.event.EventPriority parsed)) {
                    throw new IllegalArgumentException("events.onSync option 'priority' must be"
                            + " a neo.EventPriority constant (e.g. neo.EventPriority.HIGH)");
                }
                priority = parsed;
            }
            if (map.get("ignoreCancelled") != null) {
                ignoreCancelled = truthy(map.get("ignoreCancelled"));
            }
        } else if (options != null) {
            throw new IllegalArgumentException("events.onSync options must be a plain object");
        }
        var subscription = ScriptApiEnvironment.bus().onSync(name, priority, ignoreCancelled,
                (payload, cancel) -> handler.apply(payload));
        LoadedModule owner = LoadTracker.current();
        if (owner != null) {
            owner.trackSync(subscription);
        }
    }

    /** JS-style truthiness for sync option values. */
    private static boolean truthy(Object value) {
        if (value == null || Boolean.FALSE.equals(value)) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            return !text.isEmpty();
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        return true;
    }

    /**
     * Waits for one occurrence of the event; the promise settles with the
     * event payload.
     *
     * @zh 等待事件的下一次触发，Promise 以载荷落定。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<BaseEvent>")
    public CompletableFuture<Object> waitFor(String name) {
        return ScriptApiEnvironment.bus().waitFor(name);
    }

    /**
     * Waits for an occurrence matching {@code filter}; rejects (promise
     * completes exceptionally) after {@code timeoutMillis}. The filter runs
     * on the dispatch thread.
     *
     * @zh 按过滤谓词等待事件；超时后 Promise 以异常落定。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<BaseEvent>")
    public CompletableFuture<Object> waitForUntil(String name, Predicate<Object> filter, long timeoutMillis) {
        return ScriptApiEnvironment.bus().waitFor(name, filter, timeoutMillis);
    }
}
