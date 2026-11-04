package ink.tuanzi.neomacros.core.console;

import ink.tuanzi.neomacros.port.model.ChatComponent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Static fan-out hub for script console emissions (the {@code console}
 * global's backend). The engine produces {@link ScriptConsoleEvent}s and
 * dispatches them here; the product layer registers dispatchers at
 * bootstrap (the always-on log4j mirror) and per web-service start (the
 * ring + SSE feed), mirroring the {@code ScriptApiEnvironment} singleton
 * pattern. With no dispatcher installed — unit tests, engine-only tools —
 * emissions are dropped and the global stays callable.
 *
 * <p>The hub also carries the host-object view hook: when the engine's
 * console formatter meets a host value it cannot describe itself, it asks
 * the installed {@code componentView} function first so version-bound
 * product code can render game types (MC chat components) into the neutral
 * rich-text model without the version-independent layers touching them.</p>
 */
public final class ScriptConsole {

    private static final List<Consumer<ScriptConsoleEvent>> DISPATCHERS = new CopyOnWriteArrayList<>();

    /**
     * Single ordered pipeline off the script loop threads: dispatchers do
     * socket writes (SSE) and log appends, which must never run on — or
     * block — a context loop. One thread preserves global emission order,
     * which is what the ring's seq and the panel rendering assume.
     */
    private static final ExecutorService PIPELINE = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "neomacros-console");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile Function<Object, ChatComponent> componentView;

    private ScriptConsole() {
    }

    /** Registers one dispatcher (idempotent for the same reference). */
    public static void installDispatcher(Consumer<ScriptConsoleEvent> dispatcher) {
        if (!DISPATCHERS.contains(dispatcher)) {
            DISPATCHERS.add(dispatcher);
        }
    }

    /** Removes a previously installed dispatcher (reference equality). */
    public static void removeDispatcher(Consumer<ScriptConsoleEvent> dispatcher) {
        DISPATCHERS.remove(dispatcher);
    }

    /** Fans one emission out to every dispatcher (no-op when none). */
    public static void dispatch(ScriptConsoleEvent event) {
        if (DISPATCHERS.isEmpty()) {
            return;
        }
        PIPELINE.execute(() -> fanOut(event));
    }

    /** Synchronous fan-out (tests, pipeline thread). */
    static void fanOut(ScriptConsoleEvent event) {
        for (Consumer<ScriptConsoleEvent> dispatcher : DISPATCHERS) {
            try {
                dispatcher.accept(event);
            } catch (RuntimeException ignored) {
                // a broken sink must never take the script's loop thread down
            }
        }
    }

    /** True when at least one dispatcher is listening (tests/diagnostics). */
    public static boolean hasDispatchers() {
        return !DISPATCHERS.isEmpty();
    }

    /**
     * Installs the product-side host renderer: converts supported host
     * values (MC components) into the neutral rich-text model; unsupported
     * values return null and the engine falls back to generic reflection.
     */
    public static void installComponentView(Function<Object, ChatComponent> view) {
        componentView = view;
    }

    /** The installed component view, or null when absent. */
    public static ChatComponent componentView(Object hostValue) {
        Function<Object, ChatComponent> view = componentView;
        if (view == null || hostValue == null) {
            return null;
        }
        try {
            return view.apply(hostValue);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
