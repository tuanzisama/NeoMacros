package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.BossBarSnapshot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fan-out hub for boss bar updates; the ClientPacketListener mixin forwards
 * here, the fabric bootstrap registers its publishing sink, and the tracked
 * state backs live boss bar queries.
 */
public final class BossbarHub {

    /** Neutral copy of one boss bar operation. */
    public record BossbarData(String uuid, String action, String name, Float progress,
            String color) {
    }

    private static final List<Consumer<BossbarData>> SINKS = new CopyOnWriteArrayList<>();
    private static final Map<String, BossBarSnapshot> STATE = new ConcurrentHashMap<>();

    private BossbarHub() {
    }

    /** Registers a boss bar sink. */
    public static void register(Consumer<BossbarData> sink) {
        SINKS.add(sink);
    }

    /** Delivers one boss bar event and updates the tracked state. */
    public static void fire(BossbarData data) {
        apply(data);
        for (var sink : SINKS) {
            sink.accept(data);
        }
    }

    /** Applies one operation to the tracked boss bar state. */
    private static void apply(BossbarData data) {
        switch (data.action()) {
            case "add" -> STATE.put(data.uuid(), new BossBarSnapshot(data.uuid(),
                    data.name() == null ? "" : data.name(),
                    data.progress() == null ? 0.0f : data.progress(),
                    data.color() == null ? "white" : data.color()));
            case "remove" -> STATE.remove(data.uuid());
            case "updateProgress" -> STATE.computeIfPresent(data.uuid(), (id, bar) ->
                    new BossBarSnapshot(id, bar.name(),
                            data.progress() == null ? bar.progress() : data.progress(),
                            bar.color()));
            case "updateName" -> STATE.computeIfPresent(data.uuid(), (id, bar) ->
                    new BossBarSnapshot(id, data.name() == null ? bar.name() : data.name(),
                            bar.progress(), bar.color()));
            case "updateStyle" -> STATE.computeIfPresent(data.uuid(), (id, bar) ->
                    new BossBarSnapshot(id, bar.name(), bar.progress(),
                            data.color() == null ? bar.color() : data.color()));
            default -> {
            }
        }
    }

    /** The boss bars currently tracked, in insertion order. */
    public static List<BossBarSnapshot> snapshot() {
        return List.copyOf(STATE.values());
    }

    /** Clears the tracked state, e.g. when the world session ends. */
    public static void clear() {
        STATE.clear();
    }
}
