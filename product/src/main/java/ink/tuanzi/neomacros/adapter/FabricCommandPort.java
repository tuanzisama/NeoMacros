package ink.tuanzi.neomacros.adapter;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import ink.tuanzi.neomacros.core.api.CommandArg;
import ink.tuanzi.neomacros.core.api.CommandRegistry;
import ink.tuanzi.neomacros.core.api.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Command registry backed by the Fabric client command API. Commands register
 * declaratively; the handler receives the raw argument remainder string on
 * the main thread. Registered commands replay into the dispatcher whenever a
 * server session opens (the dispatcher is per-session).
 *
 * <p>Each registered tree is a literal plus one greedy tail argument: the
 * tail carries the slot's completion provider and re-runs the handler, so
 * multi-token input parses (the flat parser in {@link CommandArg} owns the
 * whole line) and tab completion reaches the script's {@code suggests}
 * metadata. Execution resolves the live registration by name, so
 * unregistering takes effect immediately and re-registering restores it.
 */
public final class FabricCommandPort implements CommandRegistry {

    private final Map<String, Entry> commands = new LinkedHashMap<>();
    /** Names unregistered since their node was built; filtered from listings. */
    private final Set<String> retired = new HashSet<>();

    /**
     * One registered command. The handler is the wrapped consumer
     * {@code CommandApi.register} installs: it takes an {@code Object[]}
     * whose first element is the raw remainder string after the command
     * name, parses the typed slots and forwards the context to the script.
     * The slots feed the completion routing.
     */
    private record Entry(String description, Consumer<Object[]> handler,
                         Map<String, CommandArg> slots) {
    }

    /** Creates the port and hooks the session replay + startup dispatch. */
    public FabricCommandPort() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            for (String name : commands.keySet()) {
                dispatcher.register(literalOf(name));
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var active = ClientCommands.getActiveDispatcher();
            if (active != null) {
                for (String name : commands.keySet()) {
                    active.getRoot().addChild(literalOf(name).build());
                }
            }
        });
    }

    /**
     * Builds one command tree: the literal runs the handler directly, and a
     * greedy tail argument swallows the rest of the line, runs the same
     * handler, and hosts the slot completion provider. Both the literal and
     * the tail resolve the live registration at call time, and the literal's
     * requirement gate skips retired names so they fall through as unknown
     * commands.
     */
    private LiteralArgumentBuilder<FabricClientCommandSource> literalOf(String name) {
        return literal(name)
                .requires(source -> commands.containsKey(name))
                .executes(ctx -> run(name, ctx.getInput()))
                .then(argument("_rest",
                                com.mojang.brigadier.arguments.StringArgumentType
                                        .greedyString())
                        .suggests((ctx, builder) -> complete(name, builder))
                        .executes(ctx -> run(name, ctx.getInput())));
    }

    /** Dispatches one parsed line into the live registration's handler. */
    private int run(String name, String input) throws CommandSyntaxException {
        Entry entry = commands.get(name);
        if (entry == null) {
            // Retired between the requirement gate and this call: behave like
            // a command that never existed and let the caller fall through.
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                    .dispatcherUnknownCommand()
                    .createWithContext(new StringReader(input));
        }
        String remainder = input.length() > name.length()
                ? input.substring(name.length()).trim() : "";
        // Raw remainder (not a token array): the wrapped handler
        // reads args[0] as the whole remainder string.
        entry.handler().accept(new Object[]{remainder});
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Serves one completion request: routes the tail through the core slot
     * metadata and replays the queued entries onto the game's builder at the
     * token offset the core computed.
     */
    private CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> complete(
            String name,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        Entry entry = commands.get(name);
        if (entry == null) {
            return builder.buildFuture();
        }
        SuggestionsBuilder queued = CommandArg.suggestAt(
                entry.slots(), builder.getInput(), builder.getStart());
        List<SuggestionsBuilder.Entry> entries = queued.entries();
        if (!entries.isEmpty()) {
            com.mojang.brigadier.suggestion.SuggestionsBuilder replay =
                    queued.getStart() == builder.getStart()
                            ? builder
                            : builder.createOffset(queued.getStart());
            for (SuggestionsBuilder.Entry candidate : entries) {
                if (candidate.tooltip() == null) {
                    replay.suggest(candidate.text());
                } else {
                    replay.suggest(candidate.text(),
                            NeutralText.toNative(candidate.tooltip().toChatModel()));
                }
            }
            if (replay != builder) {
                builder.add(replay);
            }
        }
        return builder.buildFuture();
    }

    @Override
    public Object register(Map<String, Object> spec) {
        String name = String.valueOf(spec.get("name"));
        String description = String.valueOf(spec.getOrDefault("description", ""));
        @SuppressWarnings("unchecked")
        Consumer<Object[]> handler = (Consumer<Object[]>) spec.get("handler");
        if (name == null || handler == null) {
            throw new IllegalArgumentException("command spec needs 'name' and 'handler'");
        }
        Map<String, CommandArg> slots = CommandArg.slotsFrom(spec.get("args"));
        commands.put(name, new Entry(description, handler, slots));
        retired.remove(name);

        // Attach into the live dispatcher when one is active (mid-session
        // registration); startup replay happens through the callback above.
        var dispatcher = ClientCommands.getActiveDispatcher();
        if (dispatcher != null) {
            dispatcher.getRoot().addChild(literalOf(name).build());
        }
        // Mirror the attachment into the connection tree, which backs tab
        // completion and the command listing for the current session. The
        // connection dispatcher is typed <ClientSuggestionProvider> while
        // client commands build <FabricClientCommandSource>; Fabric mixes
        // the interface into the provider, so the raw attach matches what
        // Fabric's own copy step does.
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            LiteralArgumentBuilder raw = literalOf(name);
            connection.getCommands().register(raw);
        }
        return name;
    }

    @Override
    public void unregister(String name) {
        commands.remove(name);
        // The node stays in the dispatchers (no reflection into brigadier's
        // child maps); the requirement gate stops execution, this marker
        // stops listings, and the live lookup stops completions.
        retired.add(name);
    }

    @Override
    public CompletableFuture<Boolean> execute(String commandLine) {
        String line = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().getConnection().sendCommand(line));
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public List<String> names() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (var node : connection.getCommands().getRoot().getChildren()) {
            if (!retired.contains(node.getName())) {
                names.add(node.getName());
            }
        }
        return names;
    }

    @Override
    public CompletableFuture<List<String>> suggestions(String commandPart) {
        String line = commandPart != null && commandPart.startsWith("/")
                ? commandPart.substring(1) : commandPart;
        CompletableFuture<List<String>> result = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                ClientPacketListener connection =
                        Minecraft.getInstance().getConnection();
                if (connection == null || line == null) {
                    result.complete(List.of());
                    return;
                }
                var dispatcher = connection.getCommands();
                var parse = dispatcher.parse(line, connection.getSuggestionsProvider());
                dispatcher.getCompletionSuggestions(parse).whenComplete((suggestions, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                        return;
                    }
                    List<String> texts = new ArrayList<>();
                    for (Suggestion suggestion : suggestions.getList()) {
                        texts.add(suggestion.getText());
                    }
                    result.complete(texts);
                });
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }
}
