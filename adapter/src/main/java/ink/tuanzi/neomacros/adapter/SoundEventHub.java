package ink.tuanzi.neomacros.adapter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fan-out point for world sound events; the sound mixin forwards here and
 * PortEventWiring registers its publishing sink.
 */
public final class SoundEventHub {

    private static final List<Consumer<WorldSoundData>> SINKS = new CopyOnWriteArrayList<>();

    /** Sound event data snapshot. */
    public record WorldSoundData(String soundId, double x, double y, double z,
                                 float volume, float pitch) {
    }

    private SoundEventHub() {
    }

    /** Registers a sound sink. */
    public static void register(Consumer<WorldSoundData> sink) {
        SINKS.add(sink);
    }

    /** Delivers one sound event to every registered sink. */
    public static void fire(WorldSoundData data) {
        for (Consumer<WorldSoundData> sink : SINKS) {
            sink.accept(data);
        }
    }
}
