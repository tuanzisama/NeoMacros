package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;

/**
 * Point-in-time copy of one installed mod's metadata.
 *
 * @param id       mod identifier
 * @param name     display name
 * @param version  version string
 * @param authors  author names in metadata order
 */
public record ModData(String id, String name, String version, List<String> authors) {

    public ModData {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        authors = List.copyOf(authors);
    }
}
