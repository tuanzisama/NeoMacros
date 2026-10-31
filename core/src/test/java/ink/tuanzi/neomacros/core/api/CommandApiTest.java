package ink.tuanzi.neomacros.core.api;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CommandApi over a recording registry: spec passthrough, unregister by name,
 * and execute delegation.
 */
class CommandApiTest {

    static final class RecordingRegistry implements CommandRegistry {
        final Map<String, Map<String, Object>> registered = new java.util.LinkedHashMap<>();
        final List<String> unregistered = new java.util.ArrayList<>();
        boolean executed;

        @Override
        public Object register(Map<String, Object> spec) {
            String name = String.valueOf(spec.get("name"));
            registered.put(name, spec);
            return name;
        }

        @Override
        public void unregister(String name) {
            registered.remove(name);
            unregistered.add(name);
        }

        @Override
        public CompletableFuture<Boolean> execute(String commandLine) {
            executed = true;
            return CompletableFuture.completedFuture(true);
        }
    }

    @Test
    void registerUnregisterRoundTrip() throws Exception {
        RecordingRegistry backing = new RecordingRegistry();
        CommandApi api = new CommandApi(backing);

        Object handle = api.register(Map.of(
                "name", "greet",
                "description", "Greets",
                "handler", (Consumer<Object[]>) args -> { }));

        assertTrue(backing.registered.containsKey("greet"));
        api.unregister(handle);
        assertEquals(List.of("greet"), backing.unregistered);
        assertEquals(0, backing.registered.size());
    }

    @Test
    void unregisterByNameAlsoWorks() {
        RecordingRegistry backing = new RecordingRegistry();
        CommandApi api = new CommandApi(backing);
        api.register(Map.of("name", "greet", "handler", (Consumer<Object[]>) args -> { }));

        api.unregister("greet");
        assertTrue(backing.unregistered.contains("greet"));
    }

    @Test
    void executeDelegates() throws Exception {
        RecordingRegistry backing = new RecordingRegistry();
        CommandApi api = new CommandApi(backing);

        assertTrue(api.execute("help").get(5, TimeUnit.SECONDS));
        assertTrue(backing.executed);
    }
}
