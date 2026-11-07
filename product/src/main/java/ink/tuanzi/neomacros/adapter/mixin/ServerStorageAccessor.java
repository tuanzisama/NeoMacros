package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the world storage access behind the server instance so world
 * queries can read the loaded save name on singleplayer.
 */
@Mixin(MinecraftServer.class)
public interface ServerStorageAccessor {

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess neomacros$storageSource();
}
