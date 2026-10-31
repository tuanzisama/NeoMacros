package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.ChatComponent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fan-out point for chat messages the client displays. The chat display mixin
 * calls {@link #fire(ChatComponent)}; the chat port registers its receive
 * sinks here. Sinks run on the game main thread in registration order.
 */
public final class ChatReceiveHub {

    private static final List<Consumer<ChatComponent>> SINKS = new CopyOnWriteArrayList<>();

    private ChatReceiveHub() {
    }

    /** Registers a receive sink. */
    public static void register(Consumer<ChatComponent> sink) {
        SINKS.add(sink);
    }

    /** Delivers one displayed message to every registered sink. */
    public static void fire(ChatComponent message) {
        for (Consumer<ChatComponent> sink : SINKS) {
            sink.accept(message);
        }
    }
}
