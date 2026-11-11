package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.InventoryPort;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Fan-out point for container slot clicks, fired <em>before</em> the click
 * reaches the game mode so a sink can cancel it (the click packet is never
 * sent). Manual and script-issued clicks share the path. Sinks run on the
 * game main thread in registration order; any sink returning {@code true}
 * cancels the click.
 */
public final class InventoryClickHub {

    private static final List<Function<InventoryPort.ClickSlotInfo, Boolean>> SINKS =
            new CopyOnWriteArrayList<>();

    private InventoryClickHub() {
    }

    /** Registers a click interceptor. */
    public static void register(Function<InventoryPort.ClickSlotInfo, Boolean> sink) {
        SINKS.add(sink);
    }

    /** Returns whether any sink cancels the click. */
    public static boolean fire(InventoryPort.ClickSlotInfo click) {
        boolean canceled = false;
        for (Function<InventoryPort.ClickSlotInfo, Boolean> sink : SINKS) {
            if (Boolean.TRUE.equals(sink.apply(click))) {
                canceled = true;
            }
        }
        return canceled;
    }
}
