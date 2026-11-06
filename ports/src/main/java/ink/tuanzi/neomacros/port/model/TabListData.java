package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;

/**
 * Point-in-time copy of the client tab list: header/footer text and the
 * players currently listed.
 *
 * @param header  header text, empty string when absent
 * @param footer  footer text, empty string when absent
 * @param entries players currently listed in the tab list
 */
public record TabListData(String header, String footer, List<TabListEntry> entries) {

    public TabListData {
        Objects.requireNonNull(entries, "entries cannot be null");
        entries = List.copyOf(entries);
    }

    /** One listed player row of the tab list. */
    public record TabListEntry(String uuid, String name, int latency, String gamemode,
            String displayName) {

        public TabListEntry {
            Objects.requireNonNull(uuid, "uuid cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(gamemode, "gamemode cannot be null");
        }
    }
}
