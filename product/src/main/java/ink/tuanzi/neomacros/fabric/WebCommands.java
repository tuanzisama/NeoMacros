package ink.tuanzi.neomacros.fabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import ink.tuanzi.neomacros.core.module.ScriptEntry;
import ink.tuanzi.neomacros.fabric.web.WebService;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.net.BindException;
import java.util.List;
import java.util.Optional;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * The {@code /neomacros editor} command surface — the single entry to the
 * web panel: deep links, session management, the pairing decision, and
 * paired-device management. All feedback goes through localized chat
 * lines; links are clickable.
 */
final class WebCommands {

    private WebCommands() {
    }

    /** The collaborators the commands act on. */
    record Context(WebService web, ModuleAccess modules, LinkBuilder links) {

        /** The script lookups the editor deep link needs. */
        public interface ModuleAccess {
            Optional<ScriptEntry> entry(String name);

            List<ScriptEntry> entries();
        }
    }

    /**
     * Builds the panel deep links: {@code panelUrl/?host=…&view=…&file=…&pair=…}
     * with a fresh one-time token on every link.
     */
    interface LinkBuilder {

        /** The deep link for the editor (optionally straight into one file). */
        String editorLink(String fileParam);
    }

    /** Builds the {@code editor} command subtree. */
    static LiteralArgumentBuilder<FabricClientCommandSource> subtree(Context context) {
        // Literals come before the macro argument: brigadier tries children
        // in insertion order, and `editor stop` must hit the subcommand.
        return literal("editor")
                .executes(ctx -> {
                    sendEditorLink(ctx.getSource(), context, null);
                    return Command.SINGLE_SUCCESS;
                })
                .then(literal("stop").executes(ctx -> {
                    if (!context.web().running()) {
                        feedback(ctx.getSource(), "neomacros.cmd.web.not_running");
                        return 0;
                    }
                    context.web().stop();
                    feedback(ctx.getSource(), "neomacros.cmd.web.stopped");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("sessions").executes(ctx -> {
                    if (!context.web().running()) {
                        feedback(ctx.getSource(), "neomacros.cmd.web.not_running");
                        return 0;
                    }
                    var sessions = context.web().sessions();
                    if (sessions.isEmpty()) {
                        feedback(ctx.getSource(), "neomacros.cmd.web.sessions_none");
                        return Command.SINGLE_SUCCESS;
                    }
                    feedback(ctx.getSource(), "neomacros.cmd.web.sessions_header",
                            sessions.size());
                    long now = System.currentTimeMillis();
                    for (var session : sessions) {
                        feedback(ctx.getSource(), "neomacros.cmd.web.sessions_row",
                                session.id(), session.remote(),
                                (now - session.connectedAtMillis()) / 1000);
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
                                        feedback(ctx.getSource(),
                                                "neomacros.cmd.web.not_running");
                                        return 0;
                                    }
                                    String id = StringArgumentType.getString(ctx, "id");
                                    if (context.web().disconnectSession(id)) {
                                        feedback(ctx.getSource(),
                                                "neomacros.cmd.web.disconnected", id);
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    feedback(ctx.getSource(),
                                            "neomacros.cmd.web.unknown_session", id);
                                    return 0;
                                })))
                .then(literal("rotate").executes(ctx -> {
                    if (!ensureRunning(ctx.getSource(), context)) {
                        return 0;
                    }
                    context.web().rotatePairingToken();
                    feedback(ctx.getSource(), "neomacros.cmd.web.rotated");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("approve").executes(ctx -> {
                    if (!ensureRunning(ctx.getSource(), context)) {
                        return 0;
                    }
                    if (!context.web().hasPendingPairing()) {
                        feedback(ctx.getSource(), "neomacros.cmd.web.no_pending");
                        return 0;
                    }
                    context.web().resolvePairing(true);
                    feedback(ctx.getSource(), "neomacros.cmd.web.approved");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("deny").executes(ctx -> {
                    if (!ensureRunning(ctx.getSource(), context)) {
                        return 0;
                    }
                    if (!context.web().hasPendingPairing()) {
                        feedback(ctx.getSource(), "neomacros.cmd.web.no_pending");
                        return 0;
                    }
                    context.web().resolvePairing(false);
                    feedback(ctx.getSource(), "neomacros.cmd.web.denied");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("devices").executes(ctx -> {
                    var devices = context.web().devices();
                    feedback(ctx.getSource(), "neomacros.cmd.web.devices_header",
                            devices.size());
                    for (var device : devices) {
                        feedback(ctx.getSource(), "neomacros.cmd.web.device_row",
                                device.id(), device.label(), device.algo());
                    }
                    return Command.SINGLE_SUCCESS;
                })
                        .then(literal("revoke")
                                .then(argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            if (context.web().revokeDevice(id)) {
                                                feedback(ctx.getSource(),
                                                        "neomacros.cmd.web.revoked", id);
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            feedback(ctx.getSource(),
                                                    "neomacros.cmd.web.unknown_device", id);
                                            return 0;
                                        }))))
                .then(argument("macro", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            context.modules().entries().forEach(entry ->
                                    builder.suggest(entry.name()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "macro");
                            Optional<ScriptEntry> entry = context.modules().entry(name);
                            if (entry.isEmpty()) {
                                feedback(ctx.getSource(),
                                        "neomacros.cmd.web.unknown_macro", name);
                                return 0;
                            }
                            sendEditorLink(ctx.getSource(), context, entry.get());
                            return Command.SINGLE_SUCCESS;
                        }));
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
        feedback(source, "neomacros.cmd.web.editor.start");
        try {
            context.web().start();
            return true;
        } catch (BindException e) {
            feedback(source, "neomacros.cmd.web.bind_failed");
            return false;
        } catch (java.io.IOException | RuntimeException e) {
            feedback(source, "neomacros.cmd.web.start_failed", e.getMessage());
            return false;
        }
    }

    /** Sends the clickable editor deep link (auto-starting the service). */
    private static void sendEditorLink(FabricClientCommandSource source, Context context,
                                       ScriptEntry entry) {
        if (!ensureRunning(source, context)) {
            return;
        }
        String fileParam = entry == null ? null
                : entry.category().dirName() + "/" + entry.name() + "/"
                + entry.folder().relativize(entry.entryFile())
                .toString().replace('\\', '/');
        feedback(source, "neomacros.cmd.web.editor.url");
        sendLink(source, context.links().editorLink(fileParam));
    }

    /** One chat line: the link itself, clickable, opening in the browser. */
    private static void sendLink(FabricClientCommandSource source, String link) {
        source.sendFeedback(Component.literal(link).withStyle(style -> style
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(
                        java.net.URI.create(link)))
                .withUnderlined(true)));
    }

    private static void feedback(FabricClientCommandSource source, String key, Object... args) {
        source.sendFeedback(Component.literal(UiText.tr(key, args)));
    }
}
