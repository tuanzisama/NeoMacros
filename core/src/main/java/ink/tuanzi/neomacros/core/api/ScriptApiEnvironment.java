package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.storage.ModuleStorage;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

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
    private static volatile Path storageDir;
    private static final Map<String, ModuleStorage> STORAGES = new ConcurrentHashMap<>();
    private static volatile UnaryOperator<Object> guestViewFn;

    private ScriptApiEnvironment() {
    }

    /** Installs the runtime singletons; called once by the bootstrap. */
    public static void install(EventBus eventBus, NeomacrosAdapter gameAdapter) {
        bus = eventBus;
        adapter = gameAdapter;
    }

    /** Installs the per-script storage directory; called once by the bootstrap. */
    public static void installStorage(Path dir) {
        storageDir = dir;
    }

    /**
     * Installs the engine-side guest-view function: converts host values
     * (snapshots, containers) into forms the guest can read under the current
     * host-access policy. The engine sets this during session setup.
     */
    public static void installGuestView(UnaryOperator<Object> viewFunction) {
        guestViewFn = viewFunction;
    }

    /** Converts one host value into its guest-readable form. */
    public static Object guestView(Object value) {
        UnaryOperator<Object> fn = guestViewFn;
        return fn != null ? fn.apply(value) : value;
    }

    /** The (cached) store for one script manifest name. */
    public static ModuleStorage storageFor(String scriptName) {
        Path dir = storageDir;
        if (dir == null) {
            throw new IllegalStateException("script API environment not installed");
        }
        return STORAGES.computeIfAbsent(scriptName,
                n -> new ModuleStorage(dir, n, 500, m -> { }));
    }

    /** Flushes and releases every open store (shutdown hook). */
    public static void closeStorages() {
        STORAGES.values().forEach(ModuleStorage::close);
        STORAGES.clear();
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
