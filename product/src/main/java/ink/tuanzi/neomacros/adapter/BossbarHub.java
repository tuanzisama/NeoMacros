package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.BossBarInfoData;

import java.util.List;
import java.util.Locale;
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
            String color, String style) {
    }

    private static final List<Consumer<BossbarData>> SINKS = new CopyOnWriteArrayList<>();
    private static final Map<String, BossBarInfoData> STATE = new ConcurrentHashMap<>();

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
            case "add" -> STATE.put(data.uuid(), bar(data.uuid(),
                    data.name() == null ? "" : data.name(),
                    data.progress() == null ? 0.0f : data.progress(),
                    data.color() == null ? "white" : data.color(),
                    data.style() == null ? "progress" : data.style()));
            case "remove" -> STATE.remove(data.uuid());
            case "updateProgress" -> STATE.computeIfPresent(data.uuid(), (id, bar) ->
                    bar(id, bar.name(),
                            data.progress() == null ? bar.progress() : data.progress(),
                            bar.color(), bar.style()));
            case "updateName" -> STATE.computeIfPresent(data.uuid(), (id, bar) ->
                    bar(id, data.name() == null ? bar.name() : data.name(),
                            bar.progress(), bar.color(), bar.style()));
            case "updateStyle" -> STATE.computeIfPresent(data.uuid(), (id, bar) ->
                    bar(id, bar.name(), bar.progress(),
                            data.color() == null ? bar.color() : data.color(),
                            data.style() == null ? bar.style() : data.style()));
            default -> {
            }
        }
    }

    /**
     * Builds one tracked bar snapshot; the color formatting facts (packed
     * value and lowercase formatting name) derive from the bar color.
     */
    private static BossBarInfoData bar(String uuid, String name, float progress,
            String color, String style) {
        var formatting = formattingOf(color);
        return new BossBarInfoData(uuid, name, progress, color, style,
                FormattingValues.colorValue(formatting),
                FormattingValues.colorName(formatting));
    }

    /** The formatting one bar color name maps onto; RESET when unknown. */
    private static net.minecraft.ChatFormatting formattingOf(String color) {
        try {
            return net.minecraft.world.BossEvent.BossBarColor
                    .valueOf(color.toUpperCase(Locale.ROOT)).getFormatting();
        } catch (IllegalArgumentException unknown) {
            return net.minecraft.ChatFormatting.RESET;
        }
    }

    /** The boss bars currently tracked, in insertion order. */
    public static List<BossBarInfoData> snapshot() {
        return List.copyOf(STATE.values());
    }

    /** Clears the tracked state, e.g. when the world session ends. */
    public static void clear() {
        STATE.clear();
    }
}
