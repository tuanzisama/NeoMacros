package ink.tuanzi.neomacros.adapter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleConsumer;

/**
 * Fan-out point for window scroll events; the scroll mixin forwards here and
 * the input port registers its sinks.
 */
public final class InputScrollHub {

    private static final List<DoubleConsumer> SINKS = new CopyOnWriteArrayList<>();

    private InputScrollHub() {
    }

    /** Registers a scroll sink. */
    public static void register(DoubleConsumer sink) {
        SINKS.add(sink);
    }

    /** Delivers one scroll event to every registered sink. */
    public static void fire(double yOffset) {
        for (DoubleConsumer sink : SINKS) {
            sink.accept(yOffset);
        }
    }
}
