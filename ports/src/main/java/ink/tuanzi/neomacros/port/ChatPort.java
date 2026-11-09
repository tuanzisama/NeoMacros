package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.ChatComponent;

import java.util.function.Consumer;

/**
 * Chat output services and the incoming-message source.
 *
 * <p>Sending methods target the local client: {@link #log(String)} prints to
 * the local chat HUD only, {@link #actionbar(String)} renders above the hotbar,
 * and {@link #say(String)} sends a message to the server as if typed by the
 * player. The receive sink fires for every chat message the client displays.</p>
 */
public interface ChatPort {

    /** Sends the message to the connected server (chat input path). */
    void say(String message);

    /** Prints the message to the local chat HUD only. */
    void log(String message);

    /** Renders the message on the action bar above the hotbar. */
    void actionbar(String message);

    /** Registers a sink invoked for every chat message the client displays. */
    void onChatReceive(Consumer<ChatComponent> sink);

    /**
     * Shows a title/subtitle overlay; nullable arguments skip that line and
     * non-null durations override the vanilla fade timings in ticks.
     */
    default void title(String title, String subtitle, Integer fadeIn, Integer stay,
            Integer fadeOut) {
    }

    /**
     * Shows a title/subtitle overlay from styled segment lines: a null list
     * skips that line, non-null durations override the vanilla fade timings
     * in ticks.
     */
    default void titleRich(java.util.List<ink.tuanzi.neomacros.port.model.TextSegmentData> title,
            java.util.List<ink.tuanzi.neomacros.port.model.TextSegmentData> subtitle,
            Integer fadeIn, Integer stay, Integer fadeOut) {
    }

    /** Shows a system toast notification in the screen corner. */
    default void toast(String title, String message) {
    }

    /** Shows a system toast notification from styled segment lines; a null
     * list renders an empty line. */
    default void toastRich(java.util.List<ink.tuanzi.neomacros.port.model.TextSegmentData> title,
            java.util.List<ink.tuanzi.neomacros.port.model.TextSegmentData> message) {
    }

    /**
     * Renders one styled rich text locally (chat HUD when
     * {@code actionBar} is false, the action bar when true). Sending rich
     * text to the server rides {@link #say(String)} with the plain form.
     */
    default void renderRichText(java.util.List<ink.tuanzi.neomacros.port.model.TextSegmentData> segments,
            boolean actionBar) {
    }

    /** Returns the rendered pixel width of the text in the chat font. */
    default int textWidth(String text) {
        return 0;
    }

    /**
     * Prints a local chat line whose click runs a client command (trust
     * prompts and error links). Implementations without clickable-chat
     * support degrade to {@link #log(String)}.
     */
    default void logClickable(String text, String command) {
        log(text + " (run: " + command + ")");
    }

    /**
     * Prints a local chat line whose click opens a URL in the browser (web
     * panel deep links; the vanilla open-link confirmation still applies).
     * Implementations without link support degrade to {@link #log(String)}.
     */
    default void logLink(String text, String url) {
        log(text + " " + url);
    }
}
