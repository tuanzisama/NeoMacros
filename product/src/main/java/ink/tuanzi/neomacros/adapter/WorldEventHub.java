package ink.tuanzi.neomacros.adapter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fan-out hub for client block updates; the ClientLevel mixin forwards here
 * and the fabric bootstrap registers its publishing sink.
 */
public final class WorldEventHub {

    /** Neutral copy of one block state change. */
    public record BlockUpdateData(int x, int y, int z, String blockId) {
    }

    private static final List<Consumer<BlockUpdateData>> SINKS = new CopyOnWriteArrayList<>();

    private WorldEventHub() {
    }

    /** Registers a block update sink. */
    public static void onBlockUpdate(Consumer<BlockUpdateData> sink) {
        SINKS.add(sink);
    }

    /** Delivers one block update event. */
    public static void fireBlockUpdate(BlockUpdateData data) {
        for (var sink : SINKS) {
            sink.accept(data);
        }
    }
}
