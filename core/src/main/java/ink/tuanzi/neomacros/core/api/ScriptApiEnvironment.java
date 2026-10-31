package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;

/**
 * Static installation point for the script-facing facade classes: the facade
 * instances are singletons created by the binding registry, so the runtime
 * singletons they delegate to (the event bus) are installed here once at
 * bootstrap. Facade methods called from a script resolve their per-context
 * state through the load tracker instead.
 */
public final class ScriptApiEnvironment {

    private static volatile EventBus bus;
    private static volatile NeomacrosAdapter adapter;

    private ScriptApiEnvironment() {
    }

    /** Installs the runtime singletons; called once by the bootstrap. */
    public static void install(EventBus eventBus, NeomacrosAdapter gameAdapter) {
        bus = eventBus;
        adapter = gameAdapter;
    }

    /** The installed event bus. */
    public static EventBus bus() {
        EventBus b = bus;
        if (b == null) {
            throw new IllegalStateException("script API environment not installed");
        }
        return b;
    }

    /** The installed adapter (port implementations of the running node). */
    public static NeomacrosAdapter adapter() {
        NeomacrosAdapter a = adapter;
        if (a == null) {
            throw new IllegalStateException("script API environment not installed");
        }
        return a;
    }
}
