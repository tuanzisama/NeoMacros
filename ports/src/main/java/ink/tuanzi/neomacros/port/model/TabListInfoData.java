package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;

/**
 * Point-in-time copy of the client tab list: header/footer text and the
 * players currently listed with their profile, skin and team facts.
 *
 * @param header  header text, empty string when absent
 * @param footer  footer text, empty string when absent
 * @param entries players currently listed in the tab list
 */
public record TabListInfoData(String header, String footer,
        List<TabListEntryInfoData> entries) {

    public TabListInfoData {
        Objects.requireNonNull(entries, "entries cannot be null");
        entries = List.copyOf(entries);
    }
}
