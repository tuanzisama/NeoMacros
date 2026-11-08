package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Immutable client-known display data of one advancement: the tree position
 * and what the advancement screen shows.
 *
 * <p>Neutral value object; identifiers use the namespaced form
 * ({@code minecraft:story/root}).</p>
 *
 * @param id          namespaced advancement identifier
 * @param parentId    namespaced parent identifier, or null on root advancements
 * @param title       display title plain text
 * @param description display description plain text
 * @param frame       display frame name ({@code task}, {@code goal}, {@code challenge})
 * @param hidden      whether the advancement is hidden until completed
 */
public record AdvancementData(String id, /* nullable on roots */ String parentId,
        String title, String description, String frame, boolean hidden) {

    public AdvancementData {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(frame, "frame cannot be null");
    }
}
