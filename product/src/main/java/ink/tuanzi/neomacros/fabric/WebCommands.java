package ink.tuanzi.neomacros.fabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import ink.tuanzi.neomacros.fabric.web.WebService;
import ink.tuanzi.neomacros.port.ChatPort;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.net.BindException;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * The {@code /neomacros editor} command surface — the single entry to the
 * web panel: deep links, session management, and paired-device
 * management. All feedback goes through localized chat lines on the mod
 * chat port; links are clickable.
 */
final class WebCommands {

    private WebCommands() {
    }

    /** The collaborators the commands act on. */
    record Context(WebService web, LinkBuilder links, ChatPort chat) {
    }

    /**
     * Builds the panel deep links: {@code panelUrl/?host=…&view=…&file=…&pair=…}
     * with a fresh one-time token on every link.
     */
    interface LinkBuilder {

        /** The deep link to the editor home, with a fresh pairing token. */
        String editorLink();
    }

    /** Builds the {@code editor} command subtree. */
    static LiteralArgumentBuilder<FabricClientCommandSource> subtree(Context context) {
        // Literals come before the macro argument: brigadier tries children
        // in insertion order, and `editor stop` must hit the subcommand.
        return literal("editor")
                .executes(ctx -> {
                    sendEditorLink(ctx.getSource(), context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(literal("stop").executes(ctx -> {
                    if (!context.web().running()) {
                        feedback(context, "neomacros.cmd.web.not_running");
                        return 0;
                    }
                    context.web().stop();
                    feedback(context, "neomacros.cmd.web.stopped");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("sessions").executes(ctx -> {
                    if (!context.web().running()) {
                        feedback(context, "neomacros.cmd.web.not_running");
                        return 0;
                    }
                    var sessions = context.web().sessions();
                    if (sessions.isEmpty()) {
                        feedback(context, "neomacros.cmd.web.sessions_none");
                        return Command.SINGLE_SUCCESS;
                    }
                    feedback(context, "neomacros.cmd.web.sessions_header",
                            Msg.num(sessions.size()));
                    long now = System.currentTimeMillis();
                    for (var session : sessions) {
                        feedback(context, "neomacros.cmd.web.sessions_row",
                                Msg.name(session.id()), Msg.dim(session.remote()),
                                Msg.num((now - session.connectedAtMillis()) / 1000));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("disconnect")
                        .then(argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (var session : context.web().sessions()) {
                                        builder.suggest(session.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    if (!context.web().running()) {
                                        feedback(context,
                                                "neomacros.cmd.web.not_running");
                                        return 0;
                                    }
                                    String id = StringArgumentType.getString(ctx, "id");
                                    if (context.web().disconnectSession(id)) {
                                        feedback(context,
                                                "neomacros.cmd.web.disconnected", Msg.name(id));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    feedback(context,
                                            "neomacros.cmd.web.unknown_session", Msg.name(id));
                                    return 0;
                                })))
                // Tokenless pairing decisions; the ids complete from the
                // live pending set. Approve registers the browser, trust
                // also answers future loopback browsers without asking.
                .then(literal("approve")
                        .then(argument("id", StringArgumentType.word())
                                .suggests(suggestPendingRequests(context))
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    String deviceId = context.web().approvePairRequest(id);
                                    if (deviceId != null) {
                                        feedback(context,
                                                "neomacros.cmd.web.approved", Msg.name(deviceId));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    feedback(context,
                                            "neomacros.cmd.web.no_pending_request", Msg.name(id));
                                    return 0;
                                })))
                .then(literal("trust-host")
                        .then(argument("id", StringArgumentType.word())
                                .suggests(suggestPendingRequests(context))
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    String deviceId = context.web().trustPairRequest(id);
                                    if (deviceId != null) {
                                        feedback(context,
                                                "neomacros.cmd.web.trusted", Msg.name(deviceId));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    feedback(context,
                                            "neomacros.cmd.web.no_pending_request", Msg.name(id));
                                    return 0;
                                })))
                .then(literal("deny")
                        .then(argument("id", StringArgumentType.word())
                                .suggests(suggestPendingRequests(context))
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    if (context.web().denyPairRequest(id)) {
                                        feedback(context, "neomacros.cmd.web.denied");
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    feedback(context,
                                            "neomacros.cmd.web.no_pending_request", Msg.name(id));
                                    return 0;
                                })))
                .then(literal("untrust").executes(ctx -> {
                    if (context.web().trustLoopback()) {
                        context.web().setTrustLoopback(false);
                        feedback(context, "neomacros.cmd.web.untrusted");
                        return Command.SINGLE_SUCCESS;
                    }
                    feedback(context, "neomacros.cmd.web.not_trusted");
                    return 0;
                }))
                .then(literal("devices").executes(ctx -> {
                    var devices = context.web().devices();
                    feedback(context, "neomacros.cmd.web.devices_header",
                            Msg.num(devices.size()));
                    for (var device : devices) {
                        feedback(context, "neomacros.cmd.web.device_row",
                                Msg.name(device.id()), Msg.dim(device.label()));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                        .then(literal("revoke")
                                .then(argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            if (context.web().revokeDevice(id)) {
                                                feedback(context,
                                                        "neomacros.cmd.web.revoked", Msg.name(id));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            feedback(context,
                                                    "neomacros.cmd.web.unknown_device", Msg.name(id));
                                            return 0;
                                        }))));
    }

    /** Tab completion over the live pending pairing request ids. */
    private static com.mojang.brigadier.suggestion.SuggestionProvider<FabricClientCommandSource>
            suggestPendingRequests(Context context) {
        return (ctx, builder) -> {
            for (var request : context.web().pendingPairRequests()) {
                builder.suggest(request.id());
            }
            return builder.buildFuture();
        };
    }

    /**
     * Starts the service if needed; returns false with feedback on failure.
     * The only success output is the starting hint — the link flows answer
     * with their own hint line plus the link itself.
     */
    private static boolean ensureRunning(FabricClientCommandSource source, Context context) {
        if (context.web().running()) {
            return true;
        }
        feedback(context, "neomacros.cmd.web.editor.start");
        try {
            context.web().start();
            return true;
        } catch (BindException e) {
            feedback(context, "neomacros.cmd.web.bind_failed");
            return false;
        } catch (java.io.IOException | RuntimeException e) {
            feedback(context, "neomacros.cmd.web.start_failed", e.getMessage());
            return false;
        }
    }

    /** Sends the clickable editor deep link (auto-starting the service). */
    private static void sendEditorLink(FabricClientCommandSource source, Context context) {
        if (!ensureRunning(source, context)) {
            return;
        }
        feedback(context, "neomacros.cmd.web.editor.url");
        sendLink(context, context.links().editorLink());
    }

    /** One chat line: the link itself, clickable, opening in the browser. */
    private static void sendLink(Context context, String link) {
        context.chat().logLink(Msg.name(link), link);
    }

    /** One chat line through the mod chat port (see NeoMacrosFabric.feedback). */
    private static void feedback(Context context, String key, Object... args) {
        context.chat().log(Msg.line(UiText.tr(key, args)));
    }
}
