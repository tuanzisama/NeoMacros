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
}
