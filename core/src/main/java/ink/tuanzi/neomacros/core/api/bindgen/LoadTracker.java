package ink.tuanzi.neomacros.core.api.bindgen;

import ink.tuanzi.neomacros.core.module.LoadedModule;

/**
 * Associates the calling thread with the module currently being loaded.
 * Context event loops are thread-per-context, so binding calls made from a
 * module's top-level code resolve their owner through this tracker and their
 * subscriptions attribute to that module.
 */
public final class LoadTracker {

    private static final ThreadLocal<LoadedModule> CURRENT = new ThreadLocal<>();

    private LoadTracker() {
    }

    /** Marks the calling (context loop) thread as loading {@code module}. */
    public static void begin(LoadedModule module) {
        CURRENT.set(module);
    }

    /** Clears the marker for the calling thread. */
    public static void end() {
        CURRENT.remove();
    }

    /** The module being loaded on this thread, or null outside module loading. */
    public static LoadedModule current() {
        return CURRENT.get();
    }
}
