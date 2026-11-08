package ink.tuanzi.neomacros.fabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import ink.tuanzi.neomacros.adapter.MainThread;
import ink.tuanzi.neomacros.fabric.update.UpdateChecker;
import ink.tuanzi.neomacros.fabric.update.UpdateNotice;
import ink.tuanzi.neomacros.port.ChatPort;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * The {@code /neomacros update} command surface: a manual check plus the
 * ignore controls. Feedback goes through localized chat lines on the mod
 * chat port like every other subcommand tree.
 */
final class UpdateCommands {

    private UpdateCommands() {
    }

    /** Builds the {@code update} command subtree. */
    static LiteralArgumentBuilder<FabricClientCommandSource> subtree(UpdateChecker checker,
            ChatPort chat) {
        return literal("update")
                .executes(ctx -> {
                    feedback(chat, "neomacros.cmd.update.help");
                    return Command.SINGLE_SUCCESS;
                })
                .then(literal("check").executes(ctx -> {
                    feedback(chat, "neomacros.cmd.update.checking");
                    checker.checkNow().thenAccept(notice -> MainThread.run(() -> {
                        if (notice == null) {
                            feedback(chat, "neomacros.cmd.update.up_to_date");
                        } else {
                            presentNotice(chat, notice);
                        }
                    }));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("ignore").executes(ctx -> {
                    UpdateNotice notice = checker.lastNotice();
                    if (notice == null) {
                        feedback(chat, "neomacros.cmd.update.nothing_to_ignore");
                        return 0;
                    }
                    NeoMacrosFabric.configOptions().update.ignoredVersion = notice.key();
                    NeoMacrosFabric.saveConfig();
                    feedback(chat, "neomacros.cmd.update.ignored", Msg.dim(notice.latestVersion()));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(literal("clear-ignore").executes(ctx -> {
                    NeoMacrosFabric.configOptions().update.ignoredVersion = "";
                    NeoMacrosFabric.saveConfig();
                    feedback(chat, "neomacros.cmd.update.ignore_cleared");
                    return Command.SINGLE_SUCCESS;
                }));
    }

    /** The manual-check result: one header line plus the clickable notes link. */
    private static void presentNotice(ChatPort chat, UpdateNotice notice) {
        feedback(chat, "neomacros.cmd.update.found",
                Msg.name(notice.latestVersion()), Msg.dim(notice.channel()));
        if (notice.notes() != null && !notice.notes().isBlank()) {
            chat.logLink(Msg.line(UiText.tr("neomacros.chat.update.notes")), notice.notes());
        }
    }

    /** One chat line through the mod chat port (see WebCommands.feedback). */
    private static void feedback(ChatPort chat, String key, Object... args) {
        chat.log(Msg.line(UiText.tr(key, args)));
    }
}
