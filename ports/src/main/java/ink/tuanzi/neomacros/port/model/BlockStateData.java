package ink.tuanzi.neomacros.port.model;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable copy of one registry block state: the namespaced block
 * identifier together with its vanilla state properties.
 *
 * <p>Neutral value object carried across port signatures; identifiers use
 * the namespaced form ({@code minecraft:oak_stairs}) and properties use the
 * vanilla names ({@code facing}, {@code half}, ...). A state with no
 * properties carries an empty map.</p>
 *
 * @param id    namespaced block identifier
 * @param props vanilla state properties, e.g. {@code facing=north}
 */
public record BlockStateData(String id, Map<String, String> props) {

    public BlockStateData {
        Objects.requireNonNull(id, "id cannot be null");
        props = props == null ? Map.of() : Map.copyOf(props);
    }
}
