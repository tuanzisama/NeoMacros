package ink.tuanzi.neomacros.adapter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Fan-out hub for entity lifecycle events (load/unload); the ClientLevel
 * mixin forwards here and PortEventWiring registers its publishing sinks.
 * The removal hook only sees the numeric tracking id, so the load hook
 * records each entity's id-to-uuid pair and the unload hook resolves the
 * uuid back from it; ids never seen loading (e.g. stale entries replaced
 * during login) resolve to null and the consumer skips them.
 */
public final class EntityLifecycleHub {

    /** Bounds the id→uuid map against level swaps that discard entities without individual removals. */
    private static final int MAX_TRACKED = 8192;

    private static final List<BiConsumer<String, String>> LOAD_SINKS = new CopyOnWriteArrayList<>();
    private static final List<Consumer<String>> UNLOAD_SINKS = new CopyOnWriteArrayList<>();
    private static final Map<Integer, String> UUID_BY_ID = new ConcurrentHashMap<>();

    private EntityLifecycleHub() {
    }

    /** Registers an entity load sink (uuid + entity type id). */
    public static void onLoad(BiConsumer<String, String> sink) {
        LOAD_SINKS.add(sink);
    }

    /** Registers an entity unload sink (entity uuid, null when untracked). */
    public static void onUnload(java.util.function.Consumer<String> sink) {
        UNLOAD_SINKS.add(sink);
    }

    /** Delivers one entity load event, remembering its id→uuid pair. */
    public static void fireLoad(int entityId, String uuid, String entityTypeId) {
        if (UUID_BY_ID.size() >= MAX_TRACKED) {
            UUID_BY_ID.clear();
        }
        UUID_BY_ID.put(entityId, uuid);
        for (var sink : LOAD_SINKS) {
            sink.accept(uuid, entityTypeId);
        }
    }

    /** Delivers one entity unload event with the tracked uuid (null when unknown). */
    public static void fireUnload(int entityId) {
        String uuid = UUID_BY_ID.remove(entityId);
        for (var sink : UNLOAD_SINKS) {
            sink.accept(uuid);
        }
    }
}
