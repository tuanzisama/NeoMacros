package ink.tuanzi.neomacros.adapter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Static hub classes for adapter→EventBus wiring: adapter mixins and Fabric
 * event callbacks delegate here, and PortEventWiring registers sinks that
 * bridge to the EventBus. All hubs follow the same pattern: CopyOnWriteArrayList
 * of sinks, fired in registration order on the calling thread.
 */
public final class EntityEventHub {

    private static final List<java.util.function.BiConsumer<String, Float>> DAMAGE_SINKS = new CopyOnWriteArrayList<>();
    private static final List<java.util.function.BiConsumer<String, Float>> HEAL_SINKS = new CopyOnWriteArrayList<>();

    private EntityEventHub() {
    }

    /** Registers a damage sink (entity UUID + damage amount). */
    public static void onDamage(java.util.function.BiConsumer<String, Float> sink) {
        DAMAGE_SINKS.add(sink);
    }

    /** Registers a heal sink (entity UUID + heal amount). */
    public static void onHeal(java.util.function.BiConsumer<String, Float> sink) {
        HEAL_SINKS.add(sink);
    }

    /** Fires entity damage. Called from the hurt mixin on the main thread. */
    public static void fireDamage(String entityUuid, float amount) {
        for (var sink : DAMAGE_SINKS) {
            sink.accept(entityUuid, amount);
        }
    }

    /** Fires entity heal. Called from the heal mixin on the main thread. */
    public static void fireHeal(String entityUuid, float amount) {
        for (var sink : HEAL_SINKS) {
            sink.accept(entityUuid, amount);
        }
    }
}
