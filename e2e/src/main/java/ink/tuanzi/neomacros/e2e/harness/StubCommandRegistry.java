package ink.tuanzi.neomacros.e2e.harness;

import ink.tuanzi.neomacros.core.api.CommandArg;
import ink.tuanzi.neomacros.core.api.CommandRegistry;
import ink.tuanzi.neomacros.core.api.SuggestionsBuilder;

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
 * after the command name, so the typed slot parsing runs end to end, and
 * one completion request routes through the core slot metadata the same way
 * the port's suggestion provider does.
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

    /** The registered command names, mirroring the port's tree listing. */
    @Override
    public List<String> names() {
        return List.copyOf(registered.keySet());
    }

    /**
     * Completes one partial command line the way the port does: an unknown
     * or incomplete name lists registered names by prefix, and a registered
     * command routes its tail through {@link CommandArg#suggestAt}.
     */
    @Override
    public CompletableFuture<List<String>> suggestions(String commandPart) {
        String line = commandPart != null && commandPart.startsWith("/")
                ? commandPart.substring(1) : commandPart;
        if (line == null || line.isEmpty()) {
            return CompletableFuture.completedFuture(names());
        }
        int split = line.indexOf(' ');
        String name = split < 0 ? line : line.substring(0, split);
        Map<String, Object> spec = registered.get(name);
        if (spec == null || split < 0) {
            // The command name itself (or an unknown one) completes against
            // the registered names, with an exact match suppressed the way
            // the game drops a suggestion identical to the input.
            String prefix = line.toLowerCase(java.util.Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String candidate : registered.keySet()) {
                if (candidate.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)
                        && !candidate.equals(line)) {
                    matches.add(candidate);
                }
            }
            return CompletableFuture.completedFuture(matches);
        }
        int tailStart = split < 0 ? line.length() : split + 1;
        Map<String, CommandArg> slots = spec.get("args") instanceof Map<?, ?> raw
                ? castSlots(raw) : Map.of();
        SuggestionsBuilder builder = CommandArg.suggestAt(slots, line, tailStart);
        List<String> texts = new ArrayList<>();
        for (SuggestionsBuilder.Entry entry : builder.entries()) {
            texts.add(entry.text());
        }
        return CompletableFuture.completedFuture(texts);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CommandArg> castSlots(Map<?, ?> raw) {
        Map<String, CommandArg> slots = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getValue() instanceof CommandArg arg) {
                slots.put(String.valueOf(entry.getKey()), arg);
            }
        }
        return slots;
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
