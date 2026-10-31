package ink.tuanzi.neomacros.adapter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Fan-out hub for entity lifecycle events (load/unload); the ClientLevel
 * mixin forwards here and PortEventWiring registers its publishing sinks.
 */
public final class EntityLifecycleHub {

    private static final List<BiConsumer<String, String>> LOAD_SINKS = new CopyOnWriteArrayList<>();
    private static final List<Consumer<String>> UNLOAD_SINKS = new CopyOnWriteArrayList<>();

    private EntityLifecycleHub() {
    }

    /** Registers an entity load sink (uuid + entity type id). */
    public static void onLoad(BiConsumer<String, String> sink) {
        LOAD_SINKS.add(sink);
    }

    /** Registers an entity unload sink (entity int id as string). */
    public static void onUnload(java.util.function.Consumer<String> sink) {
        UNLOAD_SINKS.add(sink);
    }

    /** Delivers one entity load event. */
    public static void fireLoad(String uuid, String entityTypeId) {
        for (var sink : LOAD_SINKS) {
            sink.accept(uuid, entityTypeId);
        }
    }

    /** Delivers one entity unload event. */
    public static void fireUnload(String entityIntId) {
        for (var sink : UNLOAD_SINKS) {
            sink.accept(entityIntId);
        }
    }
}
