package ink.tuanzi.neomacros.core.net;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Static fan-out hub for {@code neo.net} request records, mirroring the
 * {@code ScriptConsole} pattern: {@code NetApi} produces one
 * {@link NetTraceEvent} per settled request and the product layer registers
 * a dispatcher per web-service start (the ring + SSE feed). With no
 * dispatcher installed — unit tests, engine-only tools, web panel off —
 * events are dropped at negligible cost.
 *
 * @zh {@code neo.net} 请求记录的静态分发 hub，仿 {@code ScriptConsole}：每次请求落定产生一条 {@link NetTraceEvent}，product 层在 web 服务启动期间注册分发器（ring + SSE）。无监听时近零开销丢弃。
 */
public final class NetTrace {

    private static final List<Consumer<NetTraceEvent>> DISPATCHERS = new CopyOnWriteArrayList<>();

    /**
     * Single ordered pipeline off the HTTP completion threads: dispatchers
     * do socket writes (SSE) and ring appends, which must never run on — or
     * interleave across — the client's completion threads. One thread
     * preserves completion order, which the ring's seq assumes.
     */
    private static final ExecutorService PIPELINE = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "neomacros-net");
        thread.setDaemon(true);
        return thread;
    });

    private NetTrace() {
    }

    /** Registers one dispatcher (idempotent for the same reference). */
    public static void installDispatcher(Consumer<NetTraceEvent> dispatcher) {
        if (!DISPATCHERS.contains(dispatcher)) {
            DISPATCHERS.add(dispatcher);
        }
    }

    /** Removes a previously installed dispatcher (reference equality). */
    public static void removeDispatcher(Consumer<NetTraceEvent> dispatcher) {
        DISPATCHERS.remove(dispatcher);
    }

    /** Fans one record out to every dispatcher (no-op when none). */
    public static void dispatch(NetTraceEvent event) {
        if (DISPATCHERS.isEmpty()) {
            return;
        }
        PIPELINE.execute(() -> fanOut(event));
    }

    /** Synchronous fan-out (tests, pipeline thread). */
    static void fanOut(NetTraceEvent event) {
        for (Consumer<NetTraceEvent> dispatcher : DISPATCHERS) {
            try {
                dispatcher.accept(event);
            } catch (RuntimeException ignored) {
                // a broken sink must never take the caller's thread down
            }
        }
    }

    /** True when at least one dispatcher is listening (tests/diagnostics). */
    public static boolean hasDispatchers() {
        return !DISPATCHERS.isEmpty();
    }
}
