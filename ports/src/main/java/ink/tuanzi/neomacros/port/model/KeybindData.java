package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Point-in-time copy of one vanilla keybind row.
 *
 * @param name bind name ({@code key.use} style)
 * @param key  the friendly label of the currently bound key
 */
public record KeybindData(String name, String key) {

    public KeybindData {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
    }
}
