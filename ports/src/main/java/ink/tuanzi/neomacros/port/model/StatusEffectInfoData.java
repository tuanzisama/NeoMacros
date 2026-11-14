package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Point-in-time copy of one registered status effect: the instance-shaped
 * counters the client holds for a zero-duration wrap plus the effect type's
 * own facts (instantness and category flags).
 *
 * @param id         effect identifier, e.g. {@code minecraft:speed}
 * @param duration   remaining duration in ticks (0 for a registry wrap)
 * @param amplifier  zero-based amplifier level (0 for a registry wrap)
 * @param infinite   whether the duration is infinite
 * @param ambient    ambient display flag of the wrapped instance
 * @param showIcon   whether the effect display shows an icon
 * @param visible    whether the effect is shown in the effect display
 * @param instant     whether the effect type applies instantly
 * @param beneficial whether the effect type is beneficial
 * @param neutral    whether the effect type is neutral
 * @param harmful    whether the effect type is harmful
 */
public record StatusEffectInfoData(String id, int duration, int amplifier, boolean infinite,
        boolean ambient, boolean showIcon, boolean visible, boolean instant,
        boolean beneficial, boolean neutral, boolean harmful) {

    public StatusEffectInfoData {
        Objects.requireNonNull(id, "id cannot be null");
    }
}
