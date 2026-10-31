package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Immutable copy of one active status effect on the local player.
 *
 * <p>Neutral value object; the live form reads through
 * {@code PlayerPort#activeEffects} in real time.</p>
 *
 * @param effectId  registry id of the effect (e.g. {@code minecraft:speed})
 * @param amplifier effect amplifier (0 = level I)
 * @param duration  remaining ticks; a negative value means infinite
 */
public record EffectSnapshot(String effectId, int amplifier, int duration) {

    public EffectSnapshot {
        Objects.requireNonNull(effectId, "effectId cannot be null");
    }
}
