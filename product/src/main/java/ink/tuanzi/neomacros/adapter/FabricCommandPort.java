package ink.tuanzi.neomacros.adapter;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import ink.tuanzi.neomacros.core.api.CommandRegistry;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Command registry backed by the Fabric client command API. Commands register
 * declaratively; the handler receives the raw argument remainder string on
 * the main thread. Registered commands replay into the dispatcher whenever a
 * server session opens (the dispatcher is per-session).
 */
public final class FabricCommandPort implements CommandRegistry {

    private final Map<String, Entry> commands = new LinkedHashMap<>();

    /**
     * One registered command. The handler is the wrapped consumer
     * {@code CommandApi.register} installs: it takes an {@code Object[]}
     * whose first element is the raw remainder string after the command
     * name, parses the typed slots and forwards the context to the script.
     */
    private record Entry(String description, Consumer<Object[]> handler) {
    }

    /** Creates the port and hooks the session replay + startup dispatch. */
    public FabricCommandPort() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            for (Map.Entry<String, Entry> entry : commands.entrySet()) {
                dispatcher.register(literalOf(entry.getKey(), entry.getValue()));
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var active = net.fabricmc.fabric.api.client.command.v2.ClientCommands
                    .getActiveDispatcher();
            if (active != null) {
                for (Map.Entry<String, Entry> entry : commands.entrySet()) {
                    active.getRoot().addChild(
                            literalOf(entry.getKey(), entry.getValue()).build());
                }
            }
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> literalOf(
            String name, Entry entry) {
        return literal(name)
                .executes(ctx -> {
                    String input = ctx.getInput();
                    String remainder = input.length() > name.length()
                            ? input.substring(name.length()).trim() : "";
                    // Raw remainder (not a token array): the wrapped handler
                    // reads args[0] as the whole remainder string.
                    entry.handler().accept(new Object[]{remainder});
                    return Command.SINGLE_SUCCESS;
                });
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
        commands.put(name, new Entry(description, handler));

        // Attach into the live dispatcher when one is active (mid-session
        // registration); startup replay happens through the callback above.
        var dispatcher = net.fabricmc.fabric.api.client.command.v2.ClientCommands
                .getActiveDispatcher();
        if (dispatcher != null) {
            dispatcher.getRoot().addChild(literalOf(name, commands.get(name)).build());
        }
        return name;
    }

    @Override
    public void unregister(String name) {
        commands.remove(name);
    }

    @Override
    public CompletableFuture<Boolean> execute(String commandLine) {
        String line = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().getConnection().sendCommand(line));
        return CompletableFuture.completedFuture(true);
    }
}
