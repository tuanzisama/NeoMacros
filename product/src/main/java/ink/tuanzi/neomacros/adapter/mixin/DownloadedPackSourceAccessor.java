package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.ServerPackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the server-pack manager behind the downloaded pack source so the
 * option face can read the server-resource-pack policy.
 */
@Mixin(DownloadedPackSource.class)
public interface DownloadedPackSourceAccessor {

    @Accessor("manager")
    ServerPackManager neomacros$manager();
}
