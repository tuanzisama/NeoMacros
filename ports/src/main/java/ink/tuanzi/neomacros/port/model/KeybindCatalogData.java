package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Map;

/**
 * Point-in-time copy of the vanilla key-mapping catalog: the bind names in
 * registration order, the distinct category keys in first-seen order, and the
 * binds grouped by category. Category keys and bind labels follow the game
 * language, so a locale change is picked up on the next read.
 *
 * @param names            bind translation keys ({@code key.forward} style) in mapping order
 * @param categories       distinct category keys in first-seen order
 * @param bindsByCategory  category key to (translated bind name, bound-key label)
 */
public record KeybindCatalogData(
        List<String> names,
        List<String> categories,
        Map<String, Map<String, String>> bindsByCategory) {
}
