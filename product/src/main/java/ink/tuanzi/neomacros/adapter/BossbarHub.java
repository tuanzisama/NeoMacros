package ink.tuanzi.neomacros.adapter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fan-out hub for boss bar updates; the ClientPacketListener mixin forwards
 * here and the fabric bootstrap registers its publishing sink.
 */
public final class BossbarHub {

    /** Neutral copy of one boss bar operation. */
    public record BossbarData(String uuid, String action, String name, Float progress) {
    }

    private static final List<Consumer<BossbarData>> SINKS = new CopyOnWriteArrayList<>();

    private BossbarHub() {
    }

    /** Registers a boss bar sink. */
    public static void register(Consumer<BossbarData> sink) {
        SINKS.add(sink);
    }

    /** Delivers one boss bar event. */
    public static void fire(BossbarData data) {
        for (var sink : SINKS) {
            sink.accept(data);
        }
    }
}
