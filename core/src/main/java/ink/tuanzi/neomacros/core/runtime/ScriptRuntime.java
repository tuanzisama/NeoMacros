package ink.tuanzi.neomacros.core.runtime;

import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.event.SyncWatchdog;
import ink.tuanzi.neomacros.core.thread.ContextEventLoop;
import ink.tuanzi.neomacros.core.thread.MainThreadScheduler;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.core.thread.TickClock;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Runtime hub assembling every version-independent subsystem with explicit
 * injection (03 §1): the script engine, the event framework, threading, the
 * tick clock, the main-thread bridge, and the adapter-supplied ports. One
 * instance lives per game session; close tears everything down in order.
 */
public final class ScriptRuntime implements AutoCloseable {

    private final ScriptEngine engine;
    private final NeomacrosAdapter adapter;
    private final EventCatalog eventCatalog;
    private final EventBus eventBus;
    private final ScriptThreadPool threadPool;
    private final TickClock tickClock;
    private final MainThreadScheduler mainThread;
    private final SyncWatchdog watchdog;
    private final Map<Long, ScriptContext> contexts = new ConcurrentHashMap<>();
    private volatile Consumer<Throwable> errorHandler = t -> { };
    private volatile boolean closed;

    /**
     * Assembles the runtime. The adapter may be null in engine-only
     * environments (application-level tests); production bootstrap always
     * passes the node's adapter. {@code maxSyncMillis} bounds synchronous
     * dispatch and bridged main-thread tasks (default 500 ms); the runtime
     * owns the watchdog that enforces it.
     */
    public ScriptRuntime(ScriptEngine engine, NeomacrosAdapter adapter,
                         EventCatalog eventCatalog, EventBus eventBus,
                         ScriptThreadPool threadPool, long maxSyncMillis) {
        this(engine, adapter, eventCatalog, eventBus, threadPool,
                new SyncWatchdog(maxSyncMillis, violation -> { }));
    }

    /**
     * Assembles the runtime with a caller-owned watchdog, letting the bootstrap
     * share one watchdog between the event bus's sync channel and the runtime's
     * main-thread bridge (and route violations to a real sink).
     */
    public ScriptRuntime(ScriptEngine engine, NeomacrosAdapter adapter,
                         EventCatalog eventCatalog, EventBus eventBus,
                         ScriptThreadPool threadPool, SyncWatchdog watchdog) {
        this.engine = engine;
        this.adapter = adapter;
        this.eventCatalog = eventCatalog;
        this.eventBus = eventBus;
        this.threadPool = threadPool;
        this.tickClock = new TickClock();
        this.watchdog = watchdog;
        this.mainThread = new MainThreadScheduler(watchdog);
        this.mainThread.bindMainThread(Thread.currentThread());
    }

    /** The event catalog (descriptor registry shared with bindings and docs). */
    public EventCatalog eventCatalog() {
        return eventCatalog;
    }

    /** The event bus (async channel + sync cancellation channel). */
    public EventBus eventBus() {
        return eventBus;
    }

    /** The script thread pool (worker threads + shared delay scheduler). */
    public ScriptThreadPool threadPool() {
        return threadPool;
    }

    /** The tick clock the adapter advances per client tick. */
    public TickClock tickClock() {
        return tickClock;
    }

    /** The game main thread bridge (enqueue + drain hook). */
    public MainThreadScheduler mainThread() {
        return mainThread;
    }

    /** The sync watchdog guarding synchronous dispatch and bridged tasks. */
    public SyncWatchdog watchdog() {
        return watchdog;
    }

    /** The adapter-supplied ports, or null when assembled without a game. */
    public NeomacrosAdapter adapter() {
        return adapter;
    }

    /** The engine backing this runtime. */
    public ScriptEngine engine() {
        return engine;
    }

    /**
     * Installs the sink receiving script-side failures: context loop task
     * errors and watchdog violations. Call before contexts are created;
     * failures of the handler itself are ignored.
     */
    public void setErrorHandler(Consumer<Throwable> handler) {
        this.errorHandler = handler != null ? handler : t -> { };
    }

    void reportError(Throwable error) {
        try {
            errorHandler.accept(error);
        } catch (RuntimeException ignored) {
            // Reporting must never become a failure source of its own.
        }
    }

    /** Creates a script context with its own dedicated event loop thread. */
    public ScriptContext createContext(String name) {
        if (closed) {
            throw new IllegalStateException("runtime is closed");
        }
        ContextEventLoop loop = threadPool.openLoop(name, this::reportError);
        ScriptContext context = new ScriptContext(name, loop,
                new AsyncPrimitives(loop, threadPool, tickClock, mainThread));
        contexts.put(context.id(), context);
        return context;
    }

    /** Returns the live context, or null when absent or already closed. */
    public ScriptContext context(long id) {
        ScriptContext context = contexts.get(id);
        return context != null && !context.isClosed() ? context : null;
    }

    /**
     * Tears the runtime down: contexts first, then the engine, then threads.
     * Idempotent.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        contexts.values().forEach(ScriptContext::close);
        contexts.clear();
        engine.close();
        watchdog.close();
        threadPool.close();
    }
}
