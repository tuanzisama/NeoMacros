package ink.tuanzi.neomacros.fabric;

import com.mojang.brigadier.Command;
import ink.tuanzi.neomacros.core.NeoMacros;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Fabric client entrypoint.
 *
 * <p>Emits a startup log line under the {@code neomacros} logger so that boot
 * verification can grep for {@code NeoMacros bootstrap}, and registers the root
 * {@code /neomacros} client command that the command subsystem builds upon.</p>
 */
public final class NeoMacrosFabric implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(NeoMacros.MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("NeoMacros bootstrap");
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("neomacros").executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.literal("NeoMacros " + NeoMacros.VERSION + " is loaded."));
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
