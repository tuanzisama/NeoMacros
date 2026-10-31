package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.ChatComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fan-out semantics of the chat receive source: sinks are invoked in
 * registration order and every sink sees every message.
 */
class ChatReceiveHubTest {

    @Test
    void firesSinksInRegistrationOrder() {
        List<String> received = new ArrayList<>();
        ChatReceiveHub.register(m -> received.add("a:" + m.text()));
        ChatReceiveHub.register(m -> received.add("b:" + m.text()));

        ChatReceiveHub.fire(ChatComponent.plain("hello"));

        assertEquals(List.of("a:hello", "b:hello"), received);
    }

    @Test
    void everySinkSeesEveryMessage() {
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        ChatReceiveHub.register(m -> first.add(m.text()));
        ChatReceiveHub.register(m -> second.add(m.text()));

        ChatReceiveHub.fire(ChatComponent.plain("one"));
        ChatReceiveHub.fire(ChatComponent.plain("two"));

        assertEquals(List.of("one", "two"), first);
        assertEquals(List.of("one", "two"), second);
    }
}
