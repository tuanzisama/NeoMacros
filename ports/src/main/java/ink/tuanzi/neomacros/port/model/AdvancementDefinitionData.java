package ink.tuanzi.neomacros.port.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable client-known definition data of one advancement: the criterion
 * requirement groups plus the rewards the advancement grants.
 *
 * <p>Neutral value object; identifiers use the namespaced form
 * ({@code minecraft:story/mine_stone}). Inline loot tables (Minecraft 26.3+)
 * carry no registry identifier and are omitted from {@code loot}.</p>
 *
 * @param id           namespaced advancement identifier
 * @param requirements criterion requirement groups; every group must be
 *                     satisfied, and any one criterion of a group satisfies
 *                     that group
 * @param experience   experience points granted when the advancement is earned
 * @param loot         loot table identifiers granted as rewards
 * @param recipes      recipe identifiers unlocked as rewards
 */
public record AdvancementDefinitionData(String id, List<List<String>> requirements,
        int experience, List<String> loot, List<String> recipes) {

    public AdvancementDefinitionData {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(requirements, "requirements cannot be null");
        Objects.requireNonNull(loot, "loot cannot be null");
        Objects.requireNonNull(recipes, "recipes cannot be null");
        List<List<String>> groups = new ArrayList<>(requirements.size());
        for (List<String> group : requirements) {
            groups.add(List.copyOf(group));
        }
        requirements = List.copyOf(groups);
        loot = List.copyOf(loot);
        recipes = List.copyOf(recipes);
    }
}
