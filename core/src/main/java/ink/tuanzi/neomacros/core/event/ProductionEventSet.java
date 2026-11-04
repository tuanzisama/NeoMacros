package ink.tuanzi.neomacros.core.event;

import java.util.List;

/**
 * The one explicit list of built-in event payload records. Production
 * bootstrap declares this list directly (the classpath scanner cannot see
 * mod classes in nested-jar deployments), the E2E harness and the catalog
 * guard test consume the same list, so every environment registers identical
 * descriptors. Keep in sync with {@code core/event/def}.
 */
public final class ProductionEventSet {

    private ProductionEventSet() {
    }

    /** The built-in payload records in catalog order. */
    public static List<Class<?>> records() {
        return List.of(
                // tick
                ink.tuanzi.neomacros.core.event.def.tick.TickEvent.class,
                // chat
                ink.tuanzi.neomacros.core.event.def.chat.ChatSendEvent.class,
                ink.tuanzi.neomacros.core.event.def.chat.ChatReceiveEvent.class,
                ink.tuanzi.neomacros.core.event.def.input.KeyPressEvent.class,
                ink.tuanzi.neomacros.core.event.def.input.InputWheelEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerExpEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerHeldItemEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerGamemodeEvent.class,
                ink.tuanzi.neomacros.core.event.def.movement.PlayerSneakEvent.class,
                ink.tuanzi.neomacros.core.event.def.movement.PlayerSprintEvent.class,
                ink.tuanzi.neomacros.core.event.def.movement.PlayerGlidingEvent.class,
                ink.tuanzi.neomacros.core.event.def.world.WorldWeatherEvent.class,
                ink.tuanzi.neomacros.core.event.def.screen.ScreenOpenEvent.class,
                ink.tuanzi.neomacros.core.event.def.screen.ScreenCloseEvent.class,
                // lifecycle
                ink.tuanzi.neomacros.core.event.def.lifecycle.GameStartEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.GameQuitEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.WorldJoinEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.WorldLeaveEvent.class,
                // player
                ink.tuanzi.neomacros.core.event.def.player.PlayerJoinEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerLeaveEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerSpawnEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerDeathEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerRespawnEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerHealthEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerHungerEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerAirEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerDamageEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerHealEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerAttackBlockEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerAttackEntityEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerInteractBlockEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerInteractEntityEvent.class,
                ink.tuanzi.neomacros.core.event.def.player.PlayerDimensionChangeEvent.class,
                // world
                ink.tuanzi.neomacros.core.event.def.world.WorldDimensionChangeEvent.class,
                ink.tuanzi.neomacros.core.event.def.world.WorldTimeEvent.class,
                ink.tuanzi.neomacros.core.event.def.world.WorldBlockUpdateEvent.class,
                ink.tuanzi.neomacros.core.event.def.world.WorldChunkLoadEvent.class,
                ink.tuanzi.neomacros.core.event.def.world.WorldChunkUnloadEvent.class,
                ink.tuanzi.neomacros.core.event.def.world.WorldBossbarEvent.class,
                // entity
                ink.tuanzi.neomacros.core.event.def.entity.EntityLoadEvent.class,
                ink.tuanzi.neomacros.core.event.def.entity.EntityUnloadEvent.class,
                ink.tuanzi.neomacros.core.event.def.entity.EntityDamageEvent.class,
                ink.tuanzi.neomacros.core.event.def.entity.EntityHealEvent.class,
                // world extras
                ink.tuanzi.neomacros.core.event.def.world.WorldSoundEvent.class,
                // player extras
                ink.tuanzi.neomacros.core.event.def.player.PlayerEffectEvent.class);
    }
}
