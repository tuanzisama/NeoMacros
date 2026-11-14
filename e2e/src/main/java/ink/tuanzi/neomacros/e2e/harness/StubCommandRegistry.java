package ink.tuanzi.neomacros.e2e.harness;

import ink.tuanzi.neomacros.core.api.CommandRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Recording command registry for the e2e harness. It captures the
 * declarative specs scripts register and replays them the way
 * {@code FabricCommandPort} does: one dispatch hands the wrapped handler a
 * single {@code Object[]} whose first element is the raw remainder string
 * after the command name, so the typed slot parsing runs end to end.
 */
public final class StubCommandRegistry implements CommandRegistry {

    private final Map<String, Map<String, Object>> registered = new LinkedHashMap<>();
    private final List<String> unregistered = new ArrayList<>();
    private final List<String> executed = new ArrayList<>();

    @Override
    public Object register(Map<String, Object> spec) {
        String name = String.valueOf(spec.get("name"));
        if (!spec.containsKey("handler")) {
            throw new IllegalArgumentException("command spec needs 'handler'");
        }
        registered.put(name, new LinkedHashMap<>(spec));
        return name;
    }

    @Override
    public void unregister(String name) {
        registered.remove(name);
        unregistered.add(name);
    }

    @Override
    public CompletableFuture<Boolean> execute(String commandLine) {
        String line = commandLine.startsWith("/")
                ? commandLine.substring(1) : commandLine;
        int split = line.indexOf(' ');
        String name = split < 0 ? line : line.substring(0, split);
        String remainder = split < 0 ? "" : line.substring(split + 1).trim();
        executed.add(line);
        return CompletableFuture.completedFuture(dispatch(name, remainder));
    }

    /** Whether the registry currently holds the command name. */
    public boolean isRegistered(String name) {
        return registered.containsKey(name);
    }

    /** The captured spec for one command (tests inspect the built slots). */
    public Map<String, Object> spec(String name) {
        return registered.get(name);
    }

    /** Every command name unregistered through the API. */
    public List<String> unregistered() {
        return List.copyOf(unregistered);
    }

    /** Every command line pushed through {@link #execute}. */
    public List<String> executed() {
        return List.copyOf(executed);
    }

    /**
     * Dispatches one command like the Fabric port does: the wrapped handler
     * receives the raw remainder as {@code args[0]} and parses the typed
     * slots in declaration order.
     *
     * @return {@code false} when the name is not registered
     */
    public boolean dispatch(String name, String remainder) {
        Map<String, Object> spec = registered.get(name);
        if (spec == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Consumer<Object[]> handler = (Consumer<Object[]>) spec.get("handler");
        handler.accept(new Object[]{remainder});
        return true;
    }
}
