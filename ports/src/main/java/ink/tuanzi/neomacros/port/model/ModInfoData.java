package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;

/**
 * Point-in-time copy of one installed mod's full loader metadata: identity,
 * display text, declared environment and dependency ids.
 *
 * @param id           mod identifier, e.g. {@code sodium}
 * @param name         display name, may differ from the id
 * @param version      loader-reported version string
 * @param authors      author names in metadata order
 * @param description  loader-provided description, empty string when absent
 * @param env          declared environment: {@code CLIENT}, {@code SERVER}
 *                     or {@code BOTH}
 * @param dependencies declared dependency mod ids in metadata order
 */
public record ModInfoData(String id, String name, String version, List<String> authors,
        String description, String env, List<String> dependencies) {

    public ModInfoData {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        authors = List.copyOf(authors);
        description = description == null ? "" : description;
        env = env == null ? "BOTH" : env;
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
