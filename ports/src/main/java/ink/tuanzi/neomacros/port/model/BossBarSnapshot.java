package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Point-in-time copy of one client boss bar.
 *
 * @param uuid     boss bar identifier
 * @param name     displayed title
 * @param progress fill fraction from 0 to 1
 * @param color    bar color identifier, e.g. {@code red}
 */
public record BossBarSnapshot(String uuid, String name, float progress, String color) {

    public BossBarSnapshot {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(color, "color cannot be null");
    }
}
