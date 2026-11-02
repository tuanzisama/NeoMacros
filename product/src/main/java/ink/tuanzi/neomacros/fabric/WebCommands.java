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
 * The {@code /neomacros web …} and {@code /neomacros editor …} command
 * surface: service lifecycle, pairing flow, device management, and the
 * deep links that carry a one-time pairing token to the panel. All feedback
 * goes through localized chat lines; links are clickable.
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
     * Builds the panel deep links: {@code panelUrl#host=…&view=…&file=…&pair=…}
     * with a fresh one-time token on every link.
     */
    interface LinkBuilder {

        /** The deep link for the editor (optionally straight into one file). */
        String editorLink(String fileParam);
    }

    /** The two command subtrees to attach under the root command. */
    public record Subtrees(LiteralArgumentBuilder<FabricClientCommandSource> web,
                           LiteralArgumentBuilder<FabricClientCommandSource> editor) {
    }

    /** Builds the {@code web} and {@code editor} subtrees. */
    static Subtrees subtrees(Context context) {
        return new Subtrees(webSubtree(context), editorSubtree(context));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> editorSubtree(
            Context context) {
        return literal("editor")
                .executes(ctx -> {
                    sendEditorLink(ctx.getSource(), context, null);
                    return Command.SINGLE_SUCCESS;
                })
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

    private static LiteralArgumentBuilder<FabricClientCommandSource> webSubtree(Context context) {
        return literal("web")
                .then(literal("start").executes(ctx -> {
                    ensureRunning(ctx.getSource(), context, true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("stop").executes(ctx -> {
                    if (!context.web().running()) {
                        feedback(ctx.getSource(), "neomacros.cmd.web.not_running");
                        return 0;
                    }
                    context.web().stop();
                    feedback(ctx.getSource(), "neomacros.cmd.web.stopped");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("status").executes(ctx -> {
                    WebService.Status status = context.web().status();
                    feedback(ctx.getSource(), "neomacros.cmd.web.status",
                            status.running()
                                    ? UiText.tr("neomacros.cmd.web.state.running")
                                    : UiText.tr("neomacros.cmd.web.state.stopped"),
                            status.port(), status.listen(), status.uptimeMs(),
                            status.deviceCount(),
                            status.pendingLabel() == null ? "-"
                                    : UiText.tr("neomacros.cmd.web.pending",
                                    status.pendingLabel()));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("pair").executes(ctx -> {
                    if (!ensureRunning(ctx.getSource(), context, false)) {
                        return 0;
                    }
                    String link = context.links().editorLink(null);
                    feedback(ctx.getSource(), "neomacros.cmd.web.pair.url");
                    sendLink(ctx.getSource(), link);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("approve").executes(ctx -> {
                    if (!ensurePairing(context)) {
                        feedback(ctx.getSource(), "neomacros.cmd.web.no_pending");
                        return 0;
                    }
                    context.web().resolvePairing(true);
                    feedback(ctx.getSource(), "neomacros.cmd.web.approved");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("deny").executes(ctx -> {
                    if (!ensurePairing(context)) {
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
                                        }))));
    }

    /**
     * Starts the service if needed; returns false with feedback on failure.
     * The verbose form (the explicit start command) reports the port and the
     * contract URL; the link flows stay quiet — their only success output is
     * the hint line plus the link itself.
     */
    private static boolean ensureRunning(FabricClientCommandSource source, Context context,
                                         boolean announce) {
        if (context.web().running()) {
            if (announce) {
                feedback(source, "neomacros.cmd.web.already_running", context.web().port());
            }
            return true;
        }
        if (!announce) {
            feedback(source, "neomacros.cmd.web.editor.start");
        }
        try {
            int port = context.web().start();
            if (announce) {
                feedback(source, "neomacros.cmd.web.started", port);
            }
            return true;
        } catch (BindException e) {
            feedback(source, "neomacros.cmd.web.bind_failed");
            return false;
        } catch (java.io.IOException e) {
            feedback(source, "neomacros.cmd.web.start_failed", e.getMessage());
            return false;
        } catch (RuntimeException e) {
            feedback(source, "neomacros.cmd.web.start_failed", e.getMessage());
            return false;
        }
    }

    private static boolean ensurePairing(Context context) {
        return context.web().running() && context.web().hasPendingPairing();
    }

    /** Sends the clickable editor deep link (auto-starting the service). */
    private static void sendEditorLink(FabricClientCommandSource source, Context context,
                                       ScriptEntry entry) {
        if (!ensureRunning(source, context, false)) {
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
