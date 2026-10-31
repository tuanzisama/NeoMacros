package ink.tuanzi.neomacros.adapter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Fan-out hub for client chunk lifecycle events; the ClientLevel and
 * ClientChunkCache mixins forward here and the fabric bootstrap registers
 * its publishing sinks.
 */
public final class ChunkEventHub {

    private static final List<BiConsumer<Integer, Integer>> LOAD_SINKS = new CopyOnWriteArrayList<>();
    private static final List<BiConsumer<Integer, Integer>> UNLOAD_SINKS = new CopyOnWriteArrayList<>();

    private ChunkEventHub() {
    }

    /** Registers a chunk load sink (chunk X + chunk Z). */
    public static void onLoad(BiConsumer<Integer, Integer> sink) {
        LOAD_SINKS.add(sink);
    }

    /** Registers a chunk unload sink (chunk X + chunk Z). */
    public static void onUnload(BiConsumer<Integer, Integer> sink) {
        UNLOAD_SINKS.add(sink);
    }

    /** Delivers one chunk load event. */
    public static void fireLoad(int chunkX, int chunkZ) {
        for (var sink : LOAD_SINKS) {
            sink.accept(chunkX, chunkZ);
        }
    }

    /** Delivers one chunk unload event. */
    public static void fireUnload(int chunkX, int chunkZ) {
        for (var sink : UNLOAD_SINKS) {
            sink.accept(chunkX, chunkZ);
        }
    }
}
