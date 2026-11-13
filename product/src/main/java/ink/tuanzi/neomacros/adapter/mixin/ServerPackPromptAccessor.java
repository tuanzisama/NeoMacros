package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.resources.server.ServerPackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the prompt status behind the server-pack manager so the option
 * face can read back whether server-provided resource packs are rejected.
 */
@Mixin(ServerPackManager.class)
public interface ServerPackPromptAccessor {

    @Accessor("packPromptStatus")
    ServerPackManager.PackPromptStatus neomacros$packPromptStatus();
}
