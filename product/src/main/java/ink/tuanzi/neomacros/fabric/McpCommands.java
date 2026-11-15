package ink.tuanzi.neomacros.fabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import ink.tuanzi.neomacros.fabric.mcp.McpService;
import ink.tuanzi.neomacros.port.ChatPort;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.net.BindException;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * The {@code /neomacros mcp} command surface: start/stop for the MCP agent
 * server and the endpoint line for agent configs, whose click copies the
 * URL. All feedback goes through the mod chat port.
 */
final class McpCommands {

    private McpCommands() {
    }

    /** The collaborators the commands act on. */
    record Context(McpService mcp, ChatPort chat) {
    }

    /** Builds the {@code mcp} command subtree. */
    static LiteralArgumentBuilder<FabricClientCommandSource> subtree(Context context) {
        return literal("mcp")
                .executes(ctx -> {
                    if (!context.mcp().running()) {
                        feedback(context, "neomacros.cmd.mcp.status_off");
                        return Command.SINGLE_SUCCESS;
                    }
                    feedback(context, "neomacros.cmd.mcp.status_running",
                            Msg.num(context.mcp().port()),
                            Msg.num(context.mcp().sessionCount()));
                    sendConnectionDetails(context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(literal("start").executes(ctx -> {
                    if (context.mcp().running()) {
                        feedback(context, "neomacros.cmd.mcp.already_running");
                        return Command.SINGLE_SUCCESS;
                    }
                    feedback(context, "neomacros.cmd.mcp.start");
                    try {
                        context.mcp().start();
                    } catch (BindException e) {
                        feedback(context, "neomacros.cmd.mcp.bind_failed");
                        return 0;
                    } catch (java.io.IOException | RuntimeException e) {
                        feedback(context, "neomacros.cmd.mcp.start_failed", e.getMessage());
                        return 0;
                    }
                    sendConnectionDetails(context);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("stop").executes(ctx -> {
                    if (!context.mcp().running()) {
                        feedback(context, "neomacros.cmd.mcp.not_running");
                        return 0;
                    }
                    context.mcp().stop();
                    feedback(context, "neomacros.cmd.mcp.stopped");
                    return Command.SINGLE_SUCCESS;
                }));
    }

    /** The endpoint an agent config needs; the whole line copies on click. */
    private static void sendConnectionDetails(Context context) {
        context.chat().logCopyable(
                Msg.line(UiText.tr("neomacros.cmd.mcp.endpoint", context.mcp().endpointUrl())),
                context.mcp().endpointUrl());
    }

    /** One chat line through the mod chat port (see NeoMacrosFabric.feedback). */
    private static void feedback(Context context, String key, Object... args) {
        context.chat().log(Msg.line(UiText.tr(key, args)));
    }
}
