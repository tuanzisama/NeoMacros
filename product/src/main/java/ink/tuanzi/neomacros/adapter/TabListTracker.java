package ink.tuanzi.neomacros.adapter;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the latest tab list header/footer text. The tab list mixin writes on
 * every server push; world queries read on demand.
 */
public final class TabListTracker {

    private static final AtomicReference<String> HEADER = new AtomicReference<>("");
    private static final AtomicReference<String> FOOTER = new AtomicReference<>("");

    private TabListTracker() {
    }

    /** Records the latest tab list header text. */
    public static void header(String value) {
        HEADER.set(value == null ? "" : value);
    }

    /** Records the latest tab list footer text. */
    public static void footer(String value) {
        FOOTER.set(value == null ? "" : value);
    }

    /** The current header text, empty string when none was sent. */
    public static String header() {
        return HEADER.get();
    }

    /** The current footer text, empty string when none was sent. */
    public static String footer() {
        return FOOTER.get();
    }

    /** Clears both texts, e.g. when the world session ends. */
    public static void clear() {
        HEADER.set("");
        FOOTER.set("");
    }
}
